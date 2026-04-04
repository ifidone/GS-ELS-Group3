import { Component, OnDestroy, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environment';
import { AuthFacade } from '../../core/auth.facade';

type CalculatorProjectionResponse = {
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
};

type SavedCalculation = {
  id: number;
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
  createdAt: string;
  updatedAt: string;
};

/**
 * Accepts plain numbers and shorthand: 15k, 1.5M, $250,000, 2.5b (k/m/b = thousand / million / billion).
 */
function parseInvestmentAmountInput(raw: string): number | null {
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }
  const normalized = trimmed.replace(/[$,\s]/g, '').replace(/_/g, '');
  const match = /^(-?[\d.]+)\s*([kKmMbB])?$/.exec(normalized);
  if (!match) {
    return null;
  }
  const n = Number(match[1]);
  if (!Number.isFinite(n)) {
    return null;
  }
  const suffix = (match[2] ?? '').toLowerCase();
  const mult = suffix === 'k' ? 1e3 : suffix === 'm' ? 1e6 : suffix === 'b' ? 1e9 : 1;
  const value = n * mult;
  return Number.isFinite(value) ? value : null;
}

@Component({
  selector: 'app-calculator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './calculator.html',
  styleUrl: './calculator.css',
})
export class CalculatorComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private authFacade = inject(AuthFacade);
  private readonly calculatorApiUrl = `${environment.apiBaseUrl}/api/calculator/project`;
  private readonly calculationsApiUrl = `${environment.apiBaseUrl}/api/calculations`;
  private lastHistoryUid: string | null = null;

  funds = [
    { name: 'Vanguard 500 Index', ticker: 'VFIAX' },
    { name: 'Fidelity Growth Fund', ticker: 'FDGRX' },
    { name: 'Schwab S&P 500 Index', ticker: 'SWPPX' },
  ];

  /**
   * Slider uses whole months (backend accepts fractional years = months / 12).
   * 1–720 months = 1 month through 60 years.
   */
  readonly durationMinMonths = 1;
  readonly durationMaxMonths = 720;

  /** Single fund vs side-by-side comparison (same amount & horizon). */
  calculatorMode: 'single' | 'compare' = 'single';

  selectedTicker = '';
  compareTickerA = '';
  compareTickerB = '';

  /** Free-text so users can type 15k, 1M, $50,000, etc. Parsed for API calls. */
  initialAmountInput = '25000';
  /** 1 = 1 month; 120 = 10 years */
  durationMonths = 120;

  result: any = null;
  projectionLoading = false;

  compareLeft: CalculatorProjectionResponse | null = null;
  compareRight: CalculatorProjectionResponse | null = null;
  compareLoading = false;

  projectionError = '';
  history: SavedCalculation[] = [];
  historyError = '';

  private projectionDebounceHandle: ReturnType<typeof setTimeout> | null = null;
  private projectionRequestSeq = 0;
  private compareProjectionSeq = 0;
  private readonly liveDebounceMs = 180;

  constructor() {
    effect(() => {
      const user = this.authFacade.currentUser();
      const uid = user?.uid ?? null;
      if (!uid) {
        this.history = [];
        this.historyError = '';
        this.lastHistoryUid = null;
        return;
      }
      if (uid === this.lastHistoryUid) return;
      this.lastHistoryUid = uid;
      void this.loadHistory();
    });
  }

  ngOnInit() {
    void this.loadHistory();
  }

  ngOnDestroy() {
    if (this.projectionDebounceHandle !== null) {
      clearTimeout(this.projectionDebounceHandle);
    }
  }

  /** Debounced: drag slider / tweak inputs without hammering the API. */
  scheduleLiveProjection(): void {
    if (this.projectionDebounceHandle !== null) {
      clearTimeout(this.projectionDebounceHandle);
    }
    this.projectionDebounceHandle = setTimeout(() => {
      this.projectionDebounceHandle = null;
      void this.runLiveProjection();
    }, this.liveDebounceMs);
  }

  setCalculatorMode(mode: 'single' | 'compare'): void {
    if (this.calculatorMode === mode) {
      return;
    }
    this.calculatorMode = mode;
    if (mode === 'compare') {
      if (!this.compareTickerA.trim() && this.selectedTicker.trim()) {
        this.compareTickerA = this.selectedTicker;
      }
      this.result = null;
      this.projectionError = '';
    } else {
      if (!this.selectedTicker.trim() && this.compareTickerA.trim()) {
        this.selectedTicker = this.compareTickerA;
      }
      this.compareLeft = null;
      this.compareRight = null;
      this.projectionError = '';
    }
    this.scheduleLiveProjection();
  }

  /** Button: single = run + save; compare = save both sides (if loaded). */
  runPrimaryAction(): void {
    if (this.projectionDebounceHandle !== null) {
      clearTimeout(this.projectionDebounceHandle);
      this.projectionDebounceHandle = null;
    }
    if (this.calculatorMode === 'single') {
      void this.runSingleProjection({ save: true });
    } else {
      void this.saveCompareBoth();
    }
  }

  private runLiveProjection(): void {
    if (this.calculatorMode === 'single') {
      void this.runSingleProjection({ save: false });
    } else {
      void this.runCompareProjection();
    }
  }

  private async runSingleProjection(opts: { save: boolean }) {
    const ticker = this.selectedTicker?.trim() ?? '';
    const investment = parseInvestmentAmountInput(this.initialAmountInput);
    const years = this.durationMonths / 12;

    if (!ticker) {
      this.projectionError = '';
      this.result = null;
      return;
    }
    if (investment == null || !Number.isFinite(investment) || !Number.isFinite(years)) {
      return;
    }
    if (
      investment <= 0 ||
      this.durationMonths < this.durationMinMonths ||
      this.durationMonths > this.durationMaxMonths
    ) {
      this.projectionError = '';
      return;
    }

    const seq = ++this.projectionRequestSeq;
    this.projectionLoading = true;
    this.projectionError = '';

    try {
      const response = await firstValueFrom(
        this.http.post<CalculatorProjectionResponse>(this.calculatorApiUrl, {
          ticker,
          initialInvestment: investment,
          years,
        })
      );

      if (seq !== this.projectionRequestSeq) {
        return;
      }

      this.result = {
        fund: response.ticker,
        beta: response.beta,
        expectedReturn: response.expectedReturn,
        futureValue: response.futureValue.toFixed(2),
        years: response.years,
      };
      if (opts.save) {
        void this.saveCalculation(response);
      }
    } catch (error) {
      if (seq !== this.projectionRequestSeq) {
        return;
      }
      console.error('Calculator API error', error);
      this.projectionError = this.projectionErrorMessage(error);
    } finally {
      if (seq === this.projectionRequestSeq) {
        this.projectionLoading = false;
      }
    }
  }

  private async runCompareProjection(): Promise<void> {
    const tickerA = this.compareTickerA?.trim() ?? '';
    const tickerB = this.compareTickerB?.trim() ?? '';
    const investment = parseInvestmentAmountInput(this.initialAmountInput);
    const years = this.durationMonths / 12;

    if (!tickerA || !tickerB) {
      this.projectionError = '';
      this.compareLeft = null;
      this.compareRight = null;
      return;
    }
    if (investment == null || !Number.isFinite(investment) || !Number.isFinite(years)) {
      this.compareLeft = null;
      this.compareRight = null;
      return;
    }
    if (
      investment <= 0 ||
      this.durationMonths < this.durationMinMonths ||
      this.durationMonths > this.durationMaxMonths
    ) {
      this.projectionError = '';
      this.compareLeft = null;
      this.compareRight = null;
      return;
    }

    const seq = ++this.compareProjectionSeq;
    this.compareLoading = true;
    this.projectionError = '';

    const body = { initialInvestment: investment, years };
    try {
      const [outA, outB] = await Promise.allSettled([
        firstValueFrom(
          this.http.post<CalculatorProjectionResponse>(this.calculatorApiUrl, {
            ...body,
            ticker: tickerA,
          }),
        ),
        firstValueFrom(
          this.http.post<CalculatorProjectionResponse>(this.calculatorApiUrl, {
            ...body,
            ticker: tickerB,
          }),
        ),
      ]);

      if (seq !== this.compareProjectionSeq) {
        return;
      }

      this.compareLeft = outA.status === 'fulfilled' ? outA.value : null;
      this.compareRight = outB.status === 'fulfilled' ? outB.value : null;

      if (outA.status === 'fulfilled' && outB.status === 'fulfilled') {
        this.projectionError = '';
      } else if (outA.status === 'rejected' && outB.status === 'rejected') {
        const err = outA.reason;
        console.error('Compare projection failed (both)', err);
        this.projectionError = this.projectionErrorMessage(err);
      } else {
        const err = outA.status === 'rejected' ? outA.reason : outB.reason;
        console.error('Compare projection partial failure', err);
        this.projectionError =
          'One fund failed to project. Check tickers or try again. Details: ' +
          this.projectionErrorMessage(err);
      }
    } finally {
      if (seq === this.compareProjectionSeq) {
        this.compareLoading = false;
      }
    }
  }

  compareWinner(): 'a' | 'b' | 'tie' | null {
    if (!this.compareLeft || !this.compareRight) {
      return null;
    }
    const a = this.compareLeft.futureValue;
    const b = this.compareRight.futureValue;
    if (!Number.isFinite(a) || !Number.isFinite(b)) {
      return null;
    }
    const eps = 1e-6;
    if (Math.abs(a - b) <= eps) {
      return 'tie';
    }
    return a > b ? 'a' : 'b';
  }

  async saveCompareSide(side: 'a' | 'b'): Promise<void> {
    const res = side === 'a' ? this.compareLeft : this.compareRight;
    if (!res) {
      return;
    }
    await this.saveCalculation(res);
  }

  private async saveCompareBoth(): Promise<void> {
    if (this.compareLeft) {
      await this.saveCalculation(this.compareLeft);
    }
    if (this.compareRight) {
      await this.saveCalculation(this.compareRight);
    }
  }

  private projectionErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Could not update projection.';
    }
    if (error.status === 0) {
      return 'Cannot reach the API. Is the backend running?';
    }
    return `Projection failed (HTTP ${error.status}).`;
  }

  onDurationSliderChange(value: string | number): void {
    const n = Math.round(Number(value));
    if (!Number.isFinite(n)) {
      return;
    }
    this.durationMonths = Math.min(
      this.durationMaxMonths,
      Math.max(this.durationMinMonths, n),
    );
    this.scheduleLiveProjection();
  }

  /** Label next to slider and in results (e.g. "6 months", "1 year", "10 years", "2 years 3 months"). */
  horizonLabelFromMonths(months: number): string {
    const m = Math.round(months);
    if (m < 1) {
      return '—';
    }
    if (m < 12) {
      return `${m} month${m === 1 ? '' : 's'}`;
    }
    const y = Math.floor(m / 12);
    const mo = m % 12;
    const yPart = `${y} year${y === 1 ? '' : 's'}`;
    if (mo === 0) {
      return yPart;
    }
    return `${yPart} ${mo} mo`;
  }

  horizonLabelFromYears(years: number): string {
    return this.horizonLabelFromMonths(Math.round(years * 12));
  }

  /** After blur, show the resolved number so the field matches what we send to the API. */
  normalizeInvestmentDisplay(): void {
    const n = parseInvestmentAmountInput(this.initialAmountInput);
    if (n != null && n > 0) {
      this.initialAmountInput = String(Math.round(n));
    }
    this.scheduleLiveProjection();
  }

  async loadHistory() {
    try {
      const headers = await this.buildAuthHeaders();
      if (!headers) {
        this.history = [];
        return;
      }

      this.history = await firstValueFrom(
        this.http.get<SavedCalculation[]>(this.calculationsApiUrl, { headers })
      );
    } catch (error: unknown) {
      console.error('Failed to load calculation history', error);
      this.historyError = this.historyLoadErrorMessage(error);
    }
  }

  private async saveCalculation(response: CalculatorProjectionResponse) {
    try {
      const headers = await this.buildAuthHeaders();
      if (!headers) return;

      const saved = await firstValueFrom(
        this.http.post<SavedCalculation>(
          this.calculationsApiUrl,
          {
            ticker: response.ticker,
            initialInvestment: response.initialInvestment,
            years: response.years,
            beta: response.beta,
            expectedReturn: response.expectedReturn,
            futureValue: response.futureValue,
          },
          { headers }
        )
      );

      this.history = [saved, ...this.history];
    } catch (error) {
      console.error('Failed to save calculation', error);
    }
  }

  async deleteCalculation(id: number) {
    try {
      const headers = await this.buildAuthHeaders();
      if (!headers) return;

      await firstValueFrom(
        this.http.delete<void>(`${this.calculationsApiUrl}/${id}`, { headers })
      );

      this.history = this.history.filter((item) => item.id !== id);
    } catch (error) {
      console.error('Failed to delete calculation', error);
    }
  }

  private async buildAuthHeaders(): Promise<HttpHeaders | null> {
    const user = this.authFacade.currentUser();
    if (!user) return null;

    const token = await user.getIdToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  private historyLoadErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Unable to load saved calculations.';
    }
    if (error.status === 0) {
      return (
        'Cannot reach the API (Failed to fetch). Start Spring Boot on port 8080, restart `ng serve`, ' +
        'or set apiBaseUrl in environment.ts if you are not using the dev proxy.'
      );
    }
    if (error.status === 401) {
      return 'Saved calculations need a valid login. If you are signed in, configure Firebase Admin on the backend (service account JSON, same Firebase project as this app).';
    }
    if (error.status === 503) {
      const body = error.error as { hint?: string; error?: string } | undefined;
      return body?.hint ?? body?.error ?? 'Backend is not ready (often missing Firebase service account).';
    }
    return `Unable to load saved calculations (HTTP ${error.status}).`;
  }
}
