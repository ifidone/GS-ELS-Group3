import { ApplicationRef, Component, NgZone, OnDestroy, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, timeout, TimeoutError } from 'rxjs';
import { environment } from '../../../environment';
import { AuthFacade } from '../../core/auth.facade';
import { AppNavbarComponent } from '../../core/navbar/app-navbar';

type PortfolioMetadata = {
  id: number;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

type PortfolioSummary = {
  calculationCount: number;
  totalPrincipal: number;
  totalFutureValue: number;
  avgBeta: number;
};

type PreviewItem = {
  calculationId: number;
  ticker: string;
  principal: number;
  years: number;
  beta: number;
  expectedReturn: number;
  capm: number;
  futureValue: number;
};

type AllocationSlice = {
  label: string;
  totalFutureValue: number;
  percentage: number;
};

type PortfolioListItem = {
  metadata: PortfolioMetadata;
  summary: PortfolioSummary;
  previewItems: PreviewItem[];
  allocationBreakdown: AllocationSlice[];
};

type LinkedCalculationResponse = {
  calculationId: number;
  ticker: string;
  principal: number;
  years: number;
  beta: number;
  expectedReturn: number;
  capm: number;
  futureValue: number;
};

type PortfolioDetail = {
  metadata: PortfolioMetadata;
  summary: PortfolioSummary;
  projectionPoints: Array<{ year: number; totalFutureValue: number }>;
  allocationBreakdown: AllocationSlice[];
  linkedCalculations: LinkedCalculationResponse[];
};

type ErrorBody = { message?: string };

type AiPortfolioAllocation = {
  ticker: string;
  name: string;
  category: string;
  weightPercent: number;
  beta: number;
  expectedReturn: number;
};

type AiPortfolioGenerateResponse = {
  riskLabel: string;
  allocations: AiPortfolioAllocation[];
  portfolioWeightedExpectedReturn: number;
  portfolioBlendedAnnualRate: number;
  deterministicFutureValue: number;
  explanation: string;
};

type CalculatorProjectionResponse = {
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
  timeSeries: Record<string, number>;
};

type SavedCalculation = {
  id: number;
  name: string | null;
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
};

@Component({
  selector: 'app-portfolios',
  standalone: true,
  imports: [CommonModule, FormsModule, AppNavbarComponent],
  templateUrl: './portfolios.html',
  styleUrl: './portfolios.css',
})
export class PortfoliosComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly appRef = inject(ApplicationRef);
  private readonly ngZone = inject(NgZone);
  readonly authFacade = inject(AuthFacade);
  private readonly base = environment.apiBaseUrl;

  portfolios: PortfolioListItem[] = [];
  listLoading = false;
  listError = '';

  selectedName: string | null = null;
  detail: PortfolioDetail | null = null;
  /** Full-page spinner only when we have no list row to show yet. */
  detailLoading = false;
  /** True while replacing provisional detail with the full GET /portfolios/{name} response. */
  detailRefreshing = false;
  detailError = '';

  available: LinkedCalculationResponse[] = [];
  availableLoading = false;

  createName = '';
  createDescription = '';
  creating = false;

  editName = '';
  editDescription = '';
  editingMeta = false;
  savingMeta = false;

  addCalcId: number | null = null;
  addingCalc = false;

  aiModalOpen = false;
  aiAmount = 25000;
  aiYears = 10;
  aiRisk: 'LOW' | 'MEDIUM' | 'HIGH' = 'MEDIUM';
  aiGenerating = false;
  aiError = '';
  aiResult: AiPortfolioGenerateResponse | null = null;
  aiElapsedSeconds = 0;
  private aiTimerId: number | null = null;
  aiSaveName = '';
  aiSaving = false;
  aiSaveError = '';

  /** Bumps on each row click so stale detail/available HTTP responses cannot flip loading flags. */
  private portfolioPanelRequestId = 0;

  /** HttpClient request timeout (ms) for panel GETs — avoids spinners when the API never responds. */
  private readonly panelHttpTimeoutMs = 25_000;
  /** AI generate hits Newton for every catalog fund; cap wait so the UI never hangs indefinitely. */
  private readonly aiGenerateTimeoutMs = 90_000;

  private lastUid: string | null = null;
  private effectRef = effect(() => {
    const user = this.authFacade.currentUser();
    const uid = user?.uid ?? null;
    if (uid !== this.lastUid) {
      this.lastUid = uid;
      this.selectedName = null;
      this.detail = null;
      this.available = [];
      if (uid) {
        void this.loadPortfolios();
      } else {
        this.portfolios = [];
        this.listError = '';
      }
    }
  });

  ngOnDestroy(): void {
    this.stopAiTimer();
    this.effectRef.destroy();
  }

  private flushUi(): void {
    if (NgZone.isInAngularZone()) {
      this.appRef.tick();
      return;
    }
    this.ngZone.run(() => this.appRef.tick());
  }

  private portfoliosUrl(extra = ''): string {
    return `${this.base}/api/portfolios${extra}`;
  }

  private encodeName(name: string): string {
    return encodeURIComponent(name);
  }

  /** Shown immediately when picking a row; replaced by GET detail (adds projections + full calc list). */
  private provisionalDetailFromListRow(row: PortfolioListItem): PortfolioDetail {
    const linked: LinkedCalculationResponse[] = row.previewItems.map((p) => ({
      calculationId: p.calculationId,
      ticker: p.ticker,
      principal: p.principal,
      years: p.years,
      beta: p.beta,
      expectedReturn: p.expectedReturn,
      capm: p.capm,
      futureValue: p.futureValue,
    }));
    return {
      metadata: { ...row.metadata },
      summary: { ...row.summary },
      projectionPoints: [],
      allocationBreakdown: row.allocationBreakdown.map((a) => ({ ...a })),
      linkedCalculations: linked,
    };
  }

  /** JSON mutations (POST/PATCH/PUT) — includes Content-Type. */
  private async authHeaders(): Promise<HttpHeaders | null> {
    const user = this.authFacade.currentUser();
    if (!user) {
      return null;
    }
    const token = await user.getIdToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    });
  }

  /** GET/DELETE — browsers forbid a body on GET; backend reads uid from Bearer token. */
  private async bearerHeaders(): Promise<HttpHeaders | null> {
    const user = this.authFacade.currentUser();
    if (!user) {
      return null;
    }
    const token = await user.getIdToken();
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  private async uid(): Promise<string | null> {
    return this.authFacade.currentUser()?.uid ?? null;
  }

  async loadPortfolios(): Promise<void> {
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      this.portfolios = [];
      return;
    }
    this.listLoading = true;
    this.listError = '';
    try {
      this.portfolios = await firstValueFrom(
        this.http.get<PortfolioListItem[]>(this.portfoliosUrl(), { headers }),
      );
    } catch (e) {
      console.error('loadPortfolios', e);
      this.listError = this.apiErrorMessage(e, 'Could not load portfolios.');
      this.portfolios = [];
    } finally {
      this.listLoading = false;
      this.flushUi();
    }
  }

  selectPortfolio(name: string): void {
    this.portfolioPanelRequestId += 1;
    const reqId = this.portfolioPanelRequestId;

    // Clear any spinners from a superseded generation (otherwise reqId mismatch skips finally → stuck UI).
    this.detailLoading = false;
    this.detailRefreshing = false;
    this.availableLoading = false;

    this.selectedName = name;
    this.detailError = '';
    this.available = [];
    this.editingMeta = false;
    this.addCalcId = null;

    const row = this.portfolios.find((p) => p.metadata.name.trim() === name.trim());
    if (row) {
      this.detail = this.provisionalDetailFromListRow(row);
      this.detailLoading = false;
      this.editName = row.metadata.name;
      this.editDescription = row.metadata.description ?? '';
    } else {
      this.detail = null;
      this.detailLoading = true;
      this.editName = name;
      this.editDescription = '';
    }

    void this.loadDetail(name, reqId);
    void this.loadAvailable(name, reqId);
  }

  clearSelection(): void {
    this.portfolioPanelRequestId += 1;
    this.selectedName = null;
    this.detail = null;
    this.available = [];
    this.detailError = '';
    this.editingMeta = false;
    this.detailLoading = false;
    this.detailRefreshing = false;
    this.availableLoading = false;
  }

  private async loadDetail(name: string, reqId: number): Promise<void> {
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      if (reqId === this.portfolioPanelRequestId) {
        this.detailLoading = false;
        this.detailRefreshing = false;
        this.flushUi();
      }
      return;
    }
    const hadProvisional =
      this.detail !== null &&
      this.selectedName === name &&
      this.detail.metadata.name.trim() === name.trim();

    if (hadProvisional) {
      this.detailRefreshing = true;
    } else {
      this.detailLoading = true;
    }
    this.detailError = '';
    try {
      try {
        const data = await firstValueFrom(
          this.http.get<PortfolioDetail>(this.portfoliosUrl(`/${this.encodeName(name)}`), {
            headers,
            timeout: this.panelHttpTimeoutMs,
          }),
        );
        if (this.selectedName !== name || reqId !== this.portfolioPanelRequestId) {
          return;
        }
        this.detail = data;
        this.editName = this.detail.metadata.name;
        this.editDescription = this.detail.metadata.description ?? '';
      } catch (e) {
        console.error('loadDetail', e);
        if (reqId !== this.portfolioPanelRequestId) {
          return;
        }
        this.detailError = this.apiErrorMessage(e, 'Could not load portfolio.');
        if (this.selectedName === name && !hadProvisional) {
          this.detail = null;
        }
      }
    } finally {
      if (reqId === this.portfolioPanelRequestId) {
        this.detailLoading = false;
        this.detailRefreshing = false;
        this.flushUi();
      }
    }
  }

  private async loadAvailable(name: string, reqId: number): Promise<void> {
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      if (reqId === this.portfolioPanelRequestId) {
        this.availableLoading = false;
        this.flushUi();
      }
      return;
    }
    this.availableLoading = true;
    try {
      try {
        const data = await firstValueFrom(
          this.http.get<LinkedCalculationResponse[]>(
            this.portfoliosUrl(`/${this.encodeName(name)}/available-calculations`),
            { headers, timeout: this.panelHttpTimeoutMs },
          ),
        );
        if (this.selectedName !== name || reqId !== this.portfolioPanelRequestId) {
          return;
        }
        this.available = data;
      } catch (e) {
        console.error('loadAvailable', e);
        if (this.selectedName === name && reqId === this.portfolioPanelRequestId) {
          this.available = [];
        }
      }
    } finally {
      if (reqId === this.portfolioPanelRequestId) {
        this.availableLoading = false;
        this.flushUi();
      }
    }
  }

  async createPortfolio(): Promise<void> {
    const uid = await this.uid();
    const headers = await this.authHeaders();
    if (!uid || !headers) {
      return;
    }
    const name = this.createName.trim();
    if (!name) {
      return;
    }
    if (
      this.portfolios.some(
        (p) => p.metadata.name.trim().toLowerCase() === name.toLowerCase(),
      )
    ) {
      this.listError =
        'You already have a portfolio with that name. Select it in the list below, or pick a different name.';
      return;
    }

    const createdName = name;
    this.creating = true;
    this.listError = '';
    let createdOk = false;
    try {
      await firstValueFrom(
        this.http
          .post<PortfolioMetadata>(
            this.portfoliosUrl(),
            {
              uid,
              name,
              description: this.createDescription.trim() || null,
            },
            { headers },
          )
          .pipe(timeout(60_000)),
      );
      this.createName = '';
      this.createDescription = '';
      await this.loadPortfolios();
      createdOk = !this.listError;
    } catch (e) {
      console.error('createPortfolio', e);
      this.listError = this.apiErrorMessage(e, 'Could not create portfolio.');
    } finally {
      this.creating = false;
      this.flushUi();
    }

    // Never await detail fetches here: if GET /portfolios/{name} hangs, we must not leave the
    // Create button stuck in "Creating…" (finally already cleared `creating`).
    if (createdOk) {
      this.selectPortfolio(createdName);
    }
  }

  async saveMeta(): Promise<void> {
    if (!this.selectedName || !this.detail) {
      return;
    }
    const uid = await this.uid();
    const headers = await this.authHeaders();
    if (!uid || !headers) {
      return;
    }
    const pathName = this.selectedName;
    const body: { uid: string; name?: string; description?: string } = { uid };
    const trimmed = this.editName.trim();
    const nameChanged = trimmed !== this.detail.metadata.name;
    const descChanged =
      (this.editDescription ?? '') !== (this.detail.metadata.description ?? '');
    if (nameChanged) {
      if (!trimmed) {
        this.detailError = 'Portfolio name cannot be empty.';
        return;
      }
      body.name = trimmed;
    }
    if (descChanged) {
      body.description = this.editDescription.trim();
    }
    if (!nameChanged && !descChanged) {
      this.editingMeta = false;
      return;
    }
    this.savingMeta = true;
    try {
      await firstValueFrom(
        this.http.request<PortfolioMetadata>('PATCH', this.portfoliosUrl(`/${this.encodeName(pathName)}`), {
          body,
          headers,
        }),
      );
      const newName = body.name ?? this.detail.metadata.name;
      this.editingMeta = false;
      await this.loadPortfolios();
      if (newName !== this.selectedName) {
        this.selectPortfolio(newName);
      } else {
        void this.loadDetail(newName, this.portfolioPanelRequestId);
        void this.loadAvailable(newName, this.portfolioPanelRequestId);
      }
    } catch (e) {
      console.error('saveMeta', e);
      this.detailError = this.apiErrorMessage(e, 'Could not update portfolio.');
    } finally {
      this.savingMeta = false;
      this.flushUi();
    }
  }

  async deletePortfolio(name: string): Promise<void> {
    if (!confirm(`Delete portfolio “${name}”? Saved calculations stay in history; only the group is removed.`)) {
      return;
    }
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      return;
    }
    try {
      await firstValueFrom(
        this.http.delete<void>(this.portfoliosUrl(`/${this.encodeName(name)}`), { headers }),
      );
      if (this.selectedName === name) {
        this.clearSelection();
      }
      await this.loadPortfolios();
    } catch (e) {
      console.error('deletePortfolio', e);
      this.listError = this.apiErrorMessage(e, 'Could not delete portfolio.');
    } finally {
      this.flushUi();
    }
  }

  async addSelectedCalculation(): Promise<void> {
    if (!this.selectedName || this.addCalcId == null) {
      return;
    }
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      return;
    }
    this.addingCalc = true;
    try {
      await firstValueFrom(
        this.http.put<void>(
          this.portfoliosUrl(`/${this.encodeName(this.selectedName)}/items/${this.addCalcId}`),
          null,
          { headers },
        ),
      );
      this.addCalcId = null;
      const sid = this.selectedName;
      const rid = this.portfolioPanelRequestId;
      await Promise.all([
        this.loadDetail(sid, rid),
        this.loadAvailable(sid, rid),
        this.loadPortfolios(),
      ]);
    } catch (e) {
      console.error('addCalc', e);
      this.detailError = this.apiErrorMessage(e, 'Could not add calculation.');
    } finally {
      this.addingCalc = false;
      this.flushUi();
    }
  }

  async removeCalculation(calculationId: number): Promise<void> {
    if (!this.selectedName) {
      return;
    }
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      return;
    }
    try {
      await firstValueFrom(
        this.http.delete<void>(
          this.portfoliosUrl(`/${this.encodeName(this.selectedName)}/items/${calculationId}`),
          { headers },
        ),
      );
      const sid = this.selectedName;
      const rid = this.portfolioPanelRequestId;
      await Promise.all([
        this.loadDetail(sid, rid),
        this.loadAvailable(sid, rid),
        this.loadPortfolios(),
      ]);
    } catch (e) {
      console.error('removeCalc', e);
      this.detailError = this.apiErrorMessage(e, 'Could not remove calculation.');
    } finally {
      this.flushUi();
    }
  }

  openAiBuilder(): void {
    if (!this.authFacade.currentUser()) {
      return;
    }
    this.aiModalOpen = true;
    this.aiError = '';
  }

  closeAiBuilder(): void {
    this.aiModalOpen = false;
    this.stopAiTimer();
  }

  resetAiBuilder(): void {
    this.aiResult = null;
    this.aiError = '';
    this.aiSaveName = '';
    this.aiSaveError = '';
    this.stopAiTimer();
  }

  async generateAiPortfolio(): Promise<void> {
    const amt = Number(this.aiAmount);
    const yrs = Math.round(Number(this.aiYears));
    if (!Number.isFinite(amt) || amt <= 0 || !Number.isFinite(yrs) || yrs < 1) {
      this.aiError = 'Enter a positive amount and at least 1 year.';
      return;
    }
    this.aiGenerating = true;
    this.aiError = '';
    this.aiResult = null;
    this.aiSaveError = '';
    this.startAiTimer();
    try {
      this.aiResult = await firstValueFrom(
        this.http
          .post<AiPortfolioGenerateResponse>(`${this.base}/api/ai-portfolio/generate`, {
            investmentAmount: amt,
            investmentYears: yrs,
            riskTolerance: this.aiRisk,
          })
          .pipe(timeout(this.aiGenerateTimeoutMs)),
      );
    } catch (e) {
      console.error('generateAiPortfolio', e);
      this.aiError = this.apiErrorMessage(e, 'Could not generate portfolio.');
      this.aiResult = null;
    } finally {
      this.aiGenerating = false;
      this.stopAiTimer();
      this.flushUi();
    }
  }

  async saveAiPortfolio(): Promise<void> {
    if (!this.aiResult) {
      return;
    }
    const uid = await this.uid();
    const headers = await this.authHeaders();
    if (!uid || !headers) {
      return;
    }
    const name = this.aiSaveName.trim();
    if (!name) {
      this.aiSaveError = 'Enter a portfolio name.';
      return;
    }
    if (
      this.portfolios.some(
        (p) => p.metadata.name.trim().toLowerCase() === name.toLowerCase(),
      )
    ) {
      this.aiSaveError =
        'You already have a portfolio with that name. Pick a different name.';
      return;
    }

    const principal = Number(this.aiAmount);
    const years = Math.round(Number(this.aiYears));
    if (!Number.isFinite(principal) || principal <= 0 || !Number.isFinite(years) || years < 1) {
      this.aiSaveError = 'Enter a positive amount and at least 1 year.';
      return;
    }

    this.aiSaving = true;
    this.aiSaveError = '';
    try {
      await firstValueFrom(
        this.http
          .post<PortfolioMetadata>(
            this.portfoliosUrl(),
            {
              uid,
              name,
              description: this.aiResult.explanation || null,
            },
            { headers },
          )
          .pipe(timeout(60_000)),
      );

      for (const allocation of this.aiResult.allocations) {
        const weight = allocation.weightPercent / 100;
        const initialInvestment = Math.max(principal * weight, 0);
        if (initialInvestment <= 0) {
          continue;
        }
        const projection = await firstValueFrom(
          this.http
            .post<CalculatorProjectionResponse>(
              `${this.base}/api/calculator/project`,
              {
                ticker: allocation.ticker,
                initialInvestment,
                years,
              },
            )
            .pipe(timeout(45_000)),
        );
        const saved = await firstValueFrom(
          this.http
            .post<SavedCalculation>(
              `${this.base}/api/calculations`,
              {
                uid,
                ticker: projection.ticker,
                initialInvestment: projection.initialInvestment,
                years: projection.years,
                beta: projection.beta,
                expectedReturn: projection.expectedReturn,
                futureValue: projection.futureValue,
              },
              { headers },
            )
            .pipe(timeout(60_000)),
        );
        await firstValueFrom(
          this.http
            .put<void>(
              this.portfoliosUrl(`/${this.encodeName(name)}/items/${saved.id}`),
              { uid },
              { headers },
            )
            .pipe(timeout(60_000)),
        );
      }

      await this.loadPortfolios();
      this.selectPortfolio(name);
      this.aiSaveName = '';
    } catch (e) {
      console.error('saveAiPortfolio', e);
      this.aiSaveError = this.apiErrorMessage(e, 'Could not save AI portfolio.');
    } finally {
      this.aiSaving = false;
      this.flushUi();
    }
  }

  private startAiTimer(): void {
    this.stopAiTimer();
    this.aiElapsedSeconds = 0;
    this.aiTimerId = window.setInterval(() => {
      this.aiElapsedSeconds += 1;
    }, 1000);
  }

  private stopAiTimer(): void {
    if (this.aiTimerId !== null) {
      window.clearInterval(this.aiTimerId);
      this.aiTimerId = null;
    }
  }

  simulateAiPortfolio(): void {
    if (!this.aiResult) {
      return;
    }
    const principal = Number(this.aiAmount);
    const years = Math.round(Number(this.aiYears));
    const holdings = this.aiResult.allocations.map((a) => ({
      ticker: a.ticker,
      weight: a.weightPercent / 100,
      name: a.name,
      beta: a.beta,
      expectedReturn: a.expectedReturn,
    }));
    void this.router.navigate(['/calculator'], {
      state: {
        portfolioSimulation: {
          principal,
          years,
          holdings,
          blendedAnnualRate: this.aiResult.portfolioBlendedAnnualRate,
          weightedExpectedReturn: this.aiResult.portfolioWeightedExpectedReturn,
          deterministicFutureValue: this.aiResult.deterministicFutureValue,
        },
      },
    });
    this.closeAiBuilder();
  }

  private apiErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof TimeoutError) {
      return 'This is taking too long (market data for many funds). Check your connection, ensure the backend is running, then try again.';
    }
    if (
      (error instanceof DOMException || error instanceof Error) &&
      (error as Error).name === 'AbortError'
    ) {
      return 'Request timed out. Is the backend running and reachable from this app?';
    }
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'Cannot reach the API. Is the backend running (port 8080) and is the dev proxy configured?';
    }
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }
    const body = error.error as ErrorBody | string | null | undefined;
    const msg = typeof body === 'object' && body && 'message' in body ? body.message : null;
    if (error.status === 409 && msg) {
      return msg;
    }
    if (msg) {
      return msg;
    }
    return `${fallback} (HTTP ${error.status}).`;
  }
}
