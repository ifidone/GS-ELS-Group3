import { Component, OnDestroy, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, timeout, TimeoutError } from 'rxjs';
import { environment } from '../../../environment';
import { AuthFacade } from '../../core/auth.facade';

type CalculatorProjectionResponse = {
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
  timeSeries: Record<string, number>;
};

type ProjectionChartSeries = {
  ticker: string;
  color: string;
  points: Array<{ month: number; value: number }>;
  finalValue: number;
};

type ProjectionChartModel = {
  title: string;
  subtitle: string;
  empty: boolean;
  width: number;
  height: number;
  maxValue: number;
  ticks: Array<{ label: string; y: number; value: number }>;
  xTicks: Array<{ label: string; x: number }>;
  durationX: number;
  durationLabel: string;
  endLabel: string;
  series: Array<{
    ticker: string;
    color: string;
    solidPath: string;
    dashedPath: string;
    finalValue: number;
    durationValue: number;
    points: Array<{ month: number; value: number }>;
  }>;
};

type ProjectionChartTooltip = {
  anchorX: number;
  popupX: number;
  popupY: number;
  timeLabel: string;
  entries: Array<{
    ticker: string;
    color: string;
    value: number;
    pointY: number;
  }>;
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

type FundOption = {
  ticker: string;
  name: string;
  category?: string;
};

export type PortfolioSimulationHolding = {
  ticker: string;
  weight: number;
  name?: string;
  beta: number;
  expectedReturn: number;
};

export type PortfolioSimulationState = {
  principal: number;
  years: number;
  holdings: PortfolioSimulationHolding[];
  blendedAnnualRate: number;
  weightedExpectedReturn: number;
  deterministicFutureValue: number;
};

type MonteCarloResponse = {
  ticker: string;
  principal: number;
  timeYears: number;
  beta: number;
  expectedReturn: number;
  worstCase: number;
  medianCase: number;
  bestCase: number;
  deterministicFV: number;
  yearlyPaths: number[][];
};

type SimulationChartModel = {
  width: number;
  height: number;
  maxValue: number;
  ticks: Array<{ label: string; y: number; value: number }>;
  xTicks: Array<{ label: string; x: number }>;
  durationX: number;
  durationMonths: number;
  endMonths: number;
  bandPath: string;
  medianPath: string;
  deterministicPath: string;
  faintPaths: string[];
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
  private router = inject(Router);
  private authFacade = inject(AuthFacade);
  private readonly calculatorApiUrl = `${environment.apiBaseUrl}/api/calculator/project`;
  private readonly calculationsApiUrl = `${environment.apiBaseUrl}/api/calculations`;
  private readonly fundsApiUrl = `${environment.apiBaseUrl}/api/funds`;
  private readonly monteCarloApiUrl = `${environment.apiBaseUrl}/api/monte-carlo`;
  private readonly monteCarloPortfolioApiUrl = `${environment.apiBaseUrl}/api/monte-carlo/portfolio`;
  /** Backend calls Newton; bound wait so Simulation view does not spin forever. */
  private readonly monteCarloHttpTimeoutMs = 90_000;
  private lastHistoryUid: string | null = null;

  /** Router state from Portfolios → “Simulate this portfolio”; consumed in ngOnInit. */
  private pendingPortfolioSimulation: PortfolioSimulationState | null = null;

  private readonly fallbackFunds: FundOption[] = [
    { name: 'Vanguard 500 Index', ticker: 'VFIAX' },
    { name: 'Fidelity Growth Fund', ticker: 'FDGRX' },
    { name: 'Schwab S&P 500 Index', ticker: 'SWPPX' },
  ];
  funds: FundOption[] = [...this.fallbackFunds];
  fundsLoading = false;
  fundsError = '';

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

  result: CalculatorProjectionResponse | null = null;
  projectionLoading = false;

  compareLeft: CalculatorProjectionResponse | null = null;
  compareRight: CalculatorProjectionResponse | null = null;
  compareLoading = false;

  /** When set, single-fund API is skipped; projections use weighted holdings (AI portfolio handoff). */
  portfolioSimulationHoldings: PortfolioSimulationHolding[] | null = null;

  projectionViewMode: 'deterministic' | 'simulation' = 'deterministic';
  monteCarloResult: MonteCarloResponse | null = null;
  monteCarloLoading = false;
  monteCarloError = '';

  projectionError = '';
  history: SavedCalculation[] = [];
  historyError = '';
  chartTooltip: ProjectionChartTooltip | null = null;

  private projectionDebounceHandle: ReturnType<typeof setTimeout> | null = null;
  private projectionRequestSeq = 0;
  private compareProjectionSeq = 0;
  private monteCarloRequestSeq = 0;
  private readonly liveDebounceMs = 180;
  private readonly chartWidth = 560;
  private readonly chartHeight = 250;
  private readonly chartPadding = { top: 16, right: 14, bottom: 34, left: 14 };

  constructor() {
    const nav = this.router.getCurrentNavigation();
    const st = nav?.extras?.state as { portfolioSimulation?: PortfolioSimulationState } | undefined;
    this.pendingPortfolioSimulation = st?.portfolioSimulation ?? null;

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
    void this.loadFunds();
    void this.loadHistory();
    if (this.pendingPortfolioSimulation) {
      this.applyPortfolioSimulation(this.pendingPortfolioSimulation);
      this.pendingPortfolioSimulation = null;
    }
  }

  ngOnDestroy() {
    if (this.projectionDebounceHandle !== null) {
      clearTimeout(this.projectionDebounceHandle);
    }
  }

  private async loadFunds(): Promise<void> {
    this.fundsLoading = true;
    this.fundsError = '';
    try {
      const response = await firstValueFrom(
        this.http.get<Array<{ ticker: string; name: string; category?: string }>>(this.fundsApiUrl),
      );
      const mapped = (response ?? [])
        .filter((fund) => fund?.ticker && fund?.name)
        .map((fund) => ({
          ticker: fund.ticker,
          name: fund.name,
          category: fund.category,
        }))
        .sort((a, b) => a.name.localeCompare(b.name));
      this.funds = mapped.length > 0 ? mapped : [...this.fallbackFunds];
    } catch (error) {
      console.error('Failed to load funds', error);
      this.fundsError = 'Unable to load mutual funds list. Showing defaults.';
      this.funds = [...this.fallbackFunds];
    } finally {
      this.fundsLoading = false;
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
      this.projectionViewMode = 'deterministic';
      if (!this.compareTickerA.trim() && this.selectedTicker.trim()) {
        this.compareTickerA = this.selectedTicker;
      }
      this.result = null;
      this.projectionError = '';
      this.chartTooltip = null;
    } else {
      if (!this.selectedTicker.trim() && this.compareTickerA.trim()) {
        this.selectedTicker = this.compareTickerA;
      }
      this.compareLeft = null;
      this.compareRight = null;
      this.projectionError = '';
      this.chartTooltip = null;
    }
    this.scheduleLiveProjection();
  }

  /** Disables the main button when inputs are incomplete or a request is in flight. */
  primaryActionDisabled(): boolean {
    if (this.calculatorMode === 'single') {
      if (this.portfolioSimulationHoldings?.length) {
        return true;
      }
      if (this.projectionLoading) {
        return true;
      }
      return !this.singleModeInputsComplete();
    }
    if (this.compareLoading) {
      return true;
    }
    return !this.compareModeReadyToSave();
  }

  private singleModeInputsComplete(): boolean {
    const ticker = this.selectedTicker?.trim() ?? '';
    if (!ticker) {
      return false;
    }
    const inv = parseInvestmentAmountInput(this.initialAmountInput);
    if (inv == null || !Number.isFinite(inv) || inv <= 0) {
      return false;
    }
    return (
      this.durationMonths >= this.durationMinMonths &&
      this.durationMonths <= this.durationMaxMonths
    );
  }

  private compareModeReadyToSave(): boolean {
    const a = this.compareTickerA?.trim() ?? '';
    const b = this.compareTickerB?.trim() ?? '';
    if (!a || !b) {
      return false;
    }
    const inv = parseInvestmentAmountInput(this.initialAmountInput);
    if (inv == null || !Number.isFinite(inv) || inv <= 0) {
      return false;
    }
    if (
      this.durationMonths < this.durationMinMonths ||
      this.durationMonths > this.durationMaxMonths
    ) {
      return false;
    }
    return !!(this.compareLeft && this.compareRight);
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
      this.chartTooltip = null;
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
      if (this.portfolioSimulationHoldings?.length) {
        this.refreshSyntheticPortfolioResult(investment, years);
        if (seq !== this.projectionRequestSeq) {
          return;
        }
        this.chartTooltip = null;
        if (this.projectionViewMode === 'simulation') {
          void this.runMonteCarloSimulation();
        }
        if (opts.save && this.result) {
          void this.saveCalculation(this.result);
        }
        return;
      }

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

      this.result = response;
      this.chartTooltip = null;
      if (this.projectionViewMode === 'simulation') {
        void this.runMonteCarloSimulation();
      }
      if (opts.save) {
        void this.saveCalculation(response);
      }
    } catch (error) {
      if (seq !== this.projectionRequestSeq) {
        return;
      }
      console.error('Calculator API error', error);
      this.projectionError = this.projectionErrorMessage(error);
      this.chartTooltip = null;
    } finally {
      if (seq === this.projectionRequestSeq) {
        this.projectionLoading = false;
      }
    }
  }

  private applyPortfolioSimulation(raw: PortfolioSimulationState): void {
    this.calculatorMode = 'single';
    this.portfolioSimulationHoldings = raw.holdings;
    this.initialAmountInput = String(Math.round(raw.principal));
    this.durationMonths = Math.min(
      this.durationMaxMonths,
      Math.max(this.durationMinMonths, Math.round(raw.years * 12)),
    );
    this.selectedTicker = raw.holdings[0]?.ticker ?? '';
    const years = this.durationMonths / 12;
    this.refreshSyntheticPortfolioResult(raw.principal, years);
    this.projectionViewMode = 'simulation';
    this.monteCarloResult = null;
    this.monteCarloError = '';
    this.chartTooltip = null;
    void this.runMonteCarloSimulation();
  }

  private refreshSyntheticPortfolioResult(principal: number, years: number): void {
    const h = this.portfolioSimulationHoldings;
    if (!h?.length) {
      return;
    }
    let wb = 0;
    let wer = 0;
    for (const x of h) {
      wb += x.weight * x.beta;
      wer += x.weight * x.expectedReturn;
    }
    const capm = this.computeCapmRate(wb, wer);
    const fv = principal * Math.exp(capm * years);
    this.result = {
      ticker: 'PORTFOLIO',
      initialInvestment: principal,
      years,
      beta: wb,
      expectedReturn: wer,
      futureValue: fv,
      timeSeries: {},
    };
  }

  private computeCapmRate(beta: number, expectedReturn: number): number {
    const riskFreeRate = 0.04;
    const raw = riskFreeRate + beta * (expectedReturn - riskFreeRate);
    return Math.max(riskFreeRate, raw);
  }

  setProjectionViewMode(mode: 'deterministic' | 'simulation'): void {
    if (this.calculatorMode === 'compare') {
      return;
    }
    this.projectionViewMode = mode;
    this.chartTooltip = null;
    if (mode === 'simulation' && this.singleModeInputsComplete() && this.result != null) {
      void this.runMonteCarloSimulation();
    }
  }

  onTickerUserChange(): void {
    this.portfolioSimulationHoldings = null;
    this.monteCarloResult = null;
    this.scheduleLiveProjection();
  }

  private async runMonteCarloSimulation(): Promise<void> {
    const projSeqAtStart = this.projectionRequestSeq;
    const investment = parseInvestmentAmountInput(this.initialAmountInput);
    const years = this.durationMonths / 12;
    if (
      investment == null ||
      !Number.isFinite(investment) ||
      investment <= 0 ||
      !Number.isFinite(years) ||
      years <= 0 ||
      this.calculatorMode !== 'single'
    ) {
      return;
    }
    if (!this.portfolioSimulationHoldings?.length) {
      const ticker = this.selectedTicker?.trim() ?? '';
      if (!ticker) {
        return;
      }
    }

    const mcSeq = ++this.monteCarloRequestSeq;
    this.monteCarloLoading = true;
    this.monteCarloError = '';
    try {
      let data: MonteCarloResponse;
      if (this.portfolioSimulationHoldings?.length) {
        data = await firstValueFrom(
          this.http
            .post<MonteCarloResponse>(this.monteCarloPortfolioApiUrl, {
              principal: investment,
              timeYears: years,
              goalAmount: 0,
              nSimulations: 500,
              holdings: this.portfolioSimulationHoldings.map((h) => ({
                ticker: h.ticker,
                weight: h.weight,
              })),
            })
            .pipe(timeout(this.monteCarloHttpTimeoutMs)),
        );
      } else {
        const ticker = this.selectedTicker?.trim() ?? '';
        data = await firstValueFrom(
          this.http
            .post<MonteCarloResponse>(this.monteCarloApiUrl, {
              ticker,
              principal: investment,
              timeYears: years,
              goalAmount: 0,
              nSimulations: 500,
            })
            .pipe(timeout(this.monteCarloHttpTimeoutMs)),
        );
      }
      if (mcSeq !== this.monteCarloRequestSeq || projSeqAtStart !== this.projectionRequestSeq) {
        return;
      }
      this.monteCarloResult = data;
    } catch (error) {
      if (mcSeq !== this.monteCarloRequestSeq) {
        return;
      }
      console.error('Monte Carlo error', error);
      this.monteCarloError = this.projectionErrorMessage(error);
      this.monteCarloResult = null;
    } finally {
      if (mcSeq === this.monteCarloRequestSeq) {
        this.monteCarloLoading = false;
      }
    }
  }

  get simulationChartModel(): SimulationChartModel | null {
    if (
      this.projectionViewMode !== 'simulation' ||
      !this.monteCarloResult?.yearlyPaths?.length ||
      !this.result
    ) {
      return null;
    }
    const paths = this.monteCarloResult.yearlyPaths;
    const nYears = paths[0]?.length ?? 0;
    if (nYears < 2) {
      return null;
    }

    const p10: number[] = [];
    const p50: number[] = [];
    const p90: number[] = [];
    for (let y = 0; y < nYears; y++) {
      const vals = paths
        .map((p) => p[y] ?? 0)
        .filter((v) => Number.isFinite(v))
        .sort((a, b) => a - b);
      if (vals.length === 0) {
        p10.push(0);
        p50.push(0);
        p90.push(0);
      } else {
        p10.push(this.percentileSorted(vals, 10));
        p50.push(this.percentileSorted(vals, 50));
        p90.push(this.percentileSorted(vals, 90));
      }
    }

    const endMonths = this.chartEndMonths();
    let maxValue = Math.max(
      ...p90,
      ...p50,
      this.result.futureValue,
      this.monteCarloResult.bestCase ?? 0,
    );
    if (!Number.isFinite(maxValue) || maxValue <= 0) {
      maxValue = 1;
    }

    const toPath = (values: number[]): string => {
      return values
        .map((value, yi) => {
          const month = yi * 12;
          const x = this.scaleX(Math.min(month, endMonths), endMonths);
          const y = this.scaleY(value, maxValue);
          return `${yi === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
        })
        .join(' ');
    };

    const p90Path = toPath(p90);
    const p10Rev = [...p10].reverse();
    const p10PathBack = p10Rev
      .map((value, idx) => {
        const yi = nYears - 1 - idx;
        const month = yi * 12;
        const x = this.scaleX(Math.min(month, endMonths), endMonths);
        const y = this.scaleY(value, maxValue);
        return `L ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');
    const bandPath = p90Path + ' ' + p10PathBack + ' Z';

    const medianPath = toPath(p50);

    const detPoints = Array.from({ length: nYears }, (_, yi) => {
      const month = yi * 12;
      const yv = this.computeProjectionValue(
        this.result!.initialInvestment,
        this.result!.beta,
        this.result!.expectedReturn,
        yi,
      );
      return { month, value: yv };
    });
    const deterministicPath = detPoints
      .map((pt, index) => {
        const x = this.scaleX(Math.min(pt.month, endMonths), endMonths);
        const y = this.scaleY(pt.value, maxValue);
        return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');

    const faintPaths: string[] = [];
    const cap = Math.min(48, paths.length);
    for (let i = 0; i < cap; i++) {
      const row = paths[i];
      if (!row?.length) {
        continue;
      }
      faintPaths.push(
        row
          .map((value, yi) => {
            const month = yi * 12;
            const x = this.scaleX(Math.min(month, endMonths), endMonths);
            const y = this.scaleY(value, maxValue);
            return `${yi === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
          })
          .join(' '),
      );
    }

    return {
      width: this.chartWidth,
      height: this.chartHeight,
      maxValue,
      ticks: this.buildChartYTicks(maxValue),
      xTicks: this.buildChartXTicks(endMonths),
      durationX: this.scaleX(this.durationMonths, endMonths),
      durationMonths: this.durationMonths,
      endMonths,
      bandPath,
      medianPath,
      deterministicPath,
      faintPaths,
    };
  }

  private percentileSorted(sorted: number[], p: number): number {
    if (sorted.length === 0) {
      return 0;
    }
    const index = (p / 100) * (sorted.length - 1);
    const lower = Math.floor(index);
    const upper = Math.ceil(index);
    if (lower === upper) {
      return sorted[lower];
    }
    const w = index - lower;
    return sorted[lower] * (1 - w) + sorted[upper] * w;
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
      this.chartTooltip = null;
      return;
    }
    if (investment == null || !Number.isFinite(investment) || !Number.isFinite(years)) {
      this.compareLeft = null;
      this.compareRight = null;
      this.chartTooltip = null;
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
      this.chartTooltip = null;
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
      this.chartTooltip = null;

      if (outA.status === 'fulfilled' && outB.status === 'fulfilled') {
        this.projectionError = '';
      } else if (outA.status === 'rejected' && outB.status === 'rejected') {
        const err = outA.reason;
        console.error('Compare projection failed (both)', err);
        this.projectionError = this.projectionErrorMessage(err);
      } else {
        let err: unknown;
        if (outA.status === 'rejected') {
          err = outA.reason;
        } else if (outB.status === 'rejected') {
          err = outB.reason;
        } else {
          err = new Error('Compare projection failed');
        }
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
    if (error instanceof TimeoutError) {
      return 'Request timed out while fetching market data. Check the backend and network, then try again.';
    }
    if (
      (error instanceof DOMException || error instanceof Error) &&
      (error as Error).name === 'AbortError'
    ) {
      return 'Request was cancelled or timed out. Try again.';
    }
    if (!(error instanceof HttpErrorResponse)) {
      return 'Could not update projection.';
    }
    if (error.status === 0) {
      return 'Cannot reach the API. Is the backend running?';
    }
    if (error.status === 504) {
      return 'Market data took too long. Try again in a moment.';
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
    this.chartTooltip = null;
    this.scheduleLiveProjection();
  }

  onDurationYearsInputChange(value: string | number): void {
    const years = Math.max(0, Math.floor(Number(value) || 0));
    this.updateDurationFromParts(years, this.durationRemainderMonths);
  }

  onDurationMonthsInputChange(value: string | number): void {
    const months = Math.max(0, Math.floor(Number(value) || 0));
    this.updateDurationFromParts(this.durationWholeYears, months);
  }

  get durationWholeYears(): number {
    return Math.floor(this.durationMonths / 12);
  }

  get durationRemainderMonths(): number {
    return this.durationMonths % 12;
  }

  get projectionChartModel(): ProjectionChartModel {
    const series =
      this.calculatorMode === 'single'
        ? this.result
          ? [this.buildChartSeries(this.result, '#f8fafc')]
          : []
        : [
            this.compareLeft ? this.buildChartSeries(this.compareLeft, '#c4b5fd') : null,
            this.compareRight ? this.buildChartSeries(this.compareRight, '#86efac') : null,
          ].filter((entry): entry is ProjectionChartSeries => entry !== null);

    const durationLabel = this.horizonLabelFromMonths(this.durationMonths);
    const endMonths = this.chartEndMonths();
    const title = this.calculatorMode === 'single' ? 'Projection curve' : 'Comparison curves';
    const subtitle =
      this.calculatorMode === 'single'
        ? 'Solid line shows the selected investment window; dashed line extends the outlook.'
        : 'Both funds share the same investment window, then continue into the dashed future view.';

    if (series.length === 0) {
      return {
        title,
        subtitle,
        empty: true,
        width: this.chartWidth,
        height: this.chartHeight,
        maxValue: 0,
        ticks: [],
        xTicks: [],
        durationX: this.chartPadding.left,
        durationLabel,
        endLabel: this.horizonLabelFromMonths(endMonths),
        series: [],
      };
    }

    const maxValue = Math.max(...series.map((entry) => entry.finalValue));
    const xTicks = this.buildChartXTicks(endMonths);
    const renderedSeries = series.map((entry) => ({
      ticker: entry.ticker,
      color: entry.color,
      solidPath: this.buildPath(
        entry.points.filter((point) => point.month <= this.durationMonths),
        endMonths,
        maxValue,
      ),
      dashedPath: this.buildPath(
        entry.points.filter((point) => point.month >= this.durationMonths),
        endMonths,
        maxValue,
      ),
      finalValue: entry.points[entry.points.length - 1]?.value ?? entry.finalValue,
      durationValue:
        entry.points.find((point) => point.month === this.durationMonths)?.value ?? entry.finalValue,
      points: entry.points,
    }));

    return {
      title,
      subtitle,
      empty: false,
      width: this.chartWidth,
      height: this.chartHeight,
      maxValue,
      ticks: this.buildChartYTicks(maxValue),
      xTicks,
      durationX: this.scaleX(this.durationMonths, endMonths),
      durationLabel,
      endLabel: this.horizonLabelFromMonths(endMonths),
      series: renderedSeries,
    };
  }

  /** Label next to slider and in results (e.g. "6 months", "1 year", "10 years", "2 years 3 months"). */
  get chartHorizonEndLabel(): string {
    return this.horizonLabelFromMonths(this.chartEndMonths());
  }

  get chartPanelEyebrow(): string {
    if (this.calculatorMode === 'single' && this.projectionViewMode === 'simulation') {
      return 'Monte Carlo simulation';
    }
    return this.projectionChartModel.title;
  }

  get chartPanelSubtitle(): string {
    if (this.calculatorMode === 'single' && this.projectionViewMode === 'simulation') {
      return (
        'Shaded band: 10th–90th percentile outcomes. Faint lines: sample paths. ' +
        'Solid white: median simulation. Dashed: deterministic baseline.'
      );
    }
    return this.projectionChartModel.subtitle;
  }

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

  compactCurrency(value: number): string {
    if (!Number.isFinite(value)) {
      return '$0';
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(value);
  }

  /** After blur, show the resolved number so the field matches what we send to the API. */
  normalizeInvestmentDisplay(): void {
    const n = parseInvestmentAmountInput(this.initialAmountInput);
    if (n != null && n > 0) {
      this.initialAmountInput = String(Math.round(n));
    }
    this.scheduleLiveProjection();
  }

  onChartHover(event: MouseEvent, chart: ProjectionChartModel): void {
    if (chart.empty || chart.series.length === 0) {
      this.chartTooltip = null;
      return;
    }

    const element = event.currentTarget as SVGRectElement | null;
    if (!element) {
      this.chartTooltip = null;
      return;
    }

    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      this.chartTooltip = null;
      return;
    }

    const plotLeft = this.chartPadding.left;
    const plotRight = chart.width - this.chartPadding.right;
    const plotWidth = plotRight - plotLeft;
    const endMonths = this.chartEndMonths();
    const relativeX = ((event.clientX - rect.left) / rect.width) * chart.width;
    const clampedX = Math.max(plotLeft, Math.min(plotRight, relativeX));
    const month = Math.round(((clampedX - plotLeft) / plotWidth) * endMonths);

    const entries = chart.series
      .map((series) => {
        const point = series.points[month];
        if (!point) {
          return null;
        }
        return {
          ticker: series.ticker,
          color: series.color,
          value: point.value,
          pointY: this.scaleY(point.value, chart.maxValue),
        };
      })
      .filter(
        (
          entry,
        ): entry is {
          ticker: string;
          color: string;
          value: number;
          pointY: number;
        } => entry !== null,
      );

    if (entries.length === 0) {
      this.chartTooltip = null;
      return;
    }

    const x = this.scaleX(month, endMonths);
    const topY = Math.min(...entries.map((entry) => entry.pointY));

    this.chartTooltip = {
      anchorX: x,
      popupX: Math.min(chart.width - 164, x + 12),
      popupY: Math.max(this.chartPadding.top + 6, topY - 92),
      timeLabel: this.horizonLabelFromMonths(month),
      entries,
    };
  }

  clearChartHover(): void {
    this.chartTooltip = null;
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

  private updateDurationFromParts(years: number, months: number): void {
    const normalizedYears = Math.max(0, Math.floor(years));
    const normalizedMonths = Math.max(0, Math.floor(months));
    const total = normalizedYears * 12 + normalizedMonths;
    this.durationMonths = Math.min(
      this.durationMaxMonths,
      Math.max(this.durationMinMonths, total),
    );
    this.scheduleLiveProjection();
  }

  private chartEndMonths(): number {
    const extension = Math.max(24, Math.round(this.durationMonths * 0.35));
    return Math.min(this.durationMaxMonths, this.durationMonths + extension);
  }

  private buildChartSeries(response: CalculatorProjectionResponse, color: string): ProjectionChartSeries {
    const endMonths = this.chartEndMonths();
    const points = Array.from({ length: endMonths + 1 }, (_, month) => {
      const years = month / 12;
      return {
        month,
        value: this.computeProjectionValue(
          response.initialInvestment,
          response.beta,
          response.expectedReturn,
          years,
        ),
      };
    });

    return {
      ticker: response.ticker,
      color,
      points,
      finalValue: Math.max(...points.map((point) => point.value)),
    };
  }

  private buildPath(
    points: Array<{ month: number; value: number }>,
    endMonths: number,
    maxValue: number,
  ): string {
    if (points.length === 0) {
      return '';
    }

    return points
      .map((point, index) => {
        const x = this.scaleX(point.month, endMonths);
        const y = this.scaleY(point.value, maxValue);
        return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');
  }

  private buildChartYTicks(maxValue: number): Array<{ label: string; y: number; value: number }> {
    const safeMax = maxValue > 0 ? maxValue : 1;
    return Array.from({ length: 4 }, (_, index) => {
      const ratio = index / 3;
      const value = safeMax * (1 - ratio);
      return {
        label: this.compactCurrency(value),
        y: this.scaleY(value, safeMax),
        value,
      };
    });
  }

  private buildChartXTicks(endMonths: number): Array<{ label: string; x: number }> {
    const ticks = [0, Math.round(endMonths / 2), endMonths];
    return ticks.map((month) => ({
      label: month === 0 ? 'Start' : this.horizonLabelFromMonths(month),
      x: this.scaleX(month, endMonths),
    }));
  }

  private computeProjectionValue(
    initialInvestment: number,
    beta: number,
    expectedReturn: number,
    years: number,
  ): number {
    const riskFreeRate = 0.04;
    const capmRate = riskFreeRate + beta * (expectedReturn - riskFreeRate);
    return initialInvestment * Math.exp(capmRate * years);
  }

  private scaleX(month: number, endMonths: number): number {
    const plotWidth = this.chartWidth - this.chartPadding.left - this.chartPadding.right;
    if (endMonths <= 0) {
      return this.chartPadding.left;
    }
    return this.chartPadding.left + (month / endMonths) * plotWidth;
  }

  private scaleY(value: number, maxValue: number): number {
    const plotHeight = this.chartHeight - this.chartPadding.top - this.chartPadding.bottom;
    const safeMax = maxValue > 0 ? maxValue : 1;
    const ratio = Math.max(0, Math.min(1, value / safeMax));
    return this.chartPadding.top + (1 - ratio) * plotHeight;
  }
}
