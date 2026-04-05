import { Component, OnDestroy, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environment';
import { AuthFacade } from '../../core/auth.facade';

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

@Component({
  selector: 'app-portfolios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './portfolios.html',
  styleUrl: './portfolios.css',
})
export class PortfoliosComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  readonly authFacade = inject(AuthFacade);
  private readonly base = environment.apiBaseUrl;

  portfolios: PortfolioListItem[] = [];
  listLoading = false;
  listError = '';

  selectedName: string | null = null;
  detail: PortfolioDetail | null = null;
  detailLoading = false;
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
    this.effectRef.destroy();
  }

  private portfoliosUrl(extra = ''): string {
    return `${this.base}/api/portfolios${extra}`;
  }

  private encodeName(name: string): string {
    return encodeURIComponent(name);
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
    }
  }

  async selectPortfolio(name: string): Promise<void> {
    this.selectedName = name;
    this.detail = null;
    this.detailError = '';
    this.available = [];
    this.editName = name;
    this.editDescription = '';
    this.editingMeta = false;
    this.addCalcId = null;
    await Promise.all([this.loadDetail(name), this.loadAvailable(name)]);
  }

  clearSelection(): void {
    this.selectedName = null;
    this.detail = null;
    this.available = [];
    this.detailError = '';
    this.editingMeta = false;
  }

  private async loadDetail(name: string): Promise<void> {
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      return;
    }
    this.detailLoading = true;
    this.detailError = '';
    try {
      this.detail = await firstValueFrom(
        this.http.get<PortfolioDetail>(this.portfoliosUrl(`/${this.encodeName(name)}`), { headers }),
      );
      this.editName = this.detail.metadata.name;
      this.editDescription = this.detail.metadata.description ?? '';
    } catch (e) {
      console.error('loadDetail', e);
      this.detailError = this.apiErrorMessage(e, 'Could not load portfolio.');
      this.detail = null;
    } finally {
      this.detailLoading = false;
    }
  }

  private async loadAvailable(name: string): Promise<void> {
    const uid = await this.uid();
    const headers = await this.bearerHeaders();
    if (!uid || !headers) {
      return;
    }
    this.availableLoading = true;
    try {
      this.available = await firstValueFrom(
        this.http.get<LinkedCalculationResponse[]>(
          this.portfoliosUrl(`/${this.encodeName(name)}/available-calculations`),
          { headers },
        ),
      );
    } catch (e) {
      console.error('loadAvailable', e);
      this.available = [];
    } finally {
      this.availableLoading = false;
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
    this.creating = true;
    try {
      await firstValueFrom(
        this.http.post<PortfolioMetadata>(
          this.portfoliosUrl(),
          {
            uid,
            name,
            description: this.createDescription.trim() || null,
          },
          { headers },
        ),
      );
      this.createName = '';
      this.createDescription = '';
      await this.loadPortfolios();
    } catch (e) {
      console.error('createPortfolio', e);
      this.listError = this.apiErrorMessage(e, 'Could not create portfolio.');
    } finally {
      this.creating = false;
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
        await this.selectPortfolio(newName);
      } else {
        await this.loadDetail(newName);
        await this.loadAvailable(newName);
      }
    } catch (e) {
      console.error('saveMeta', e);
      this.detailError = this.apiErrorMessage(e, 'Could not update portfolio.');
    } finally {
      this.savingMeta = false;
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
      await Promise.all([this.loadDetail(this.selectedName), this.loadAvailable(this.selectedName), this.loadPortfolios()]);
    } catch (e) {
      console.error('addCalc', e);
      this.detailError = this.apiErrorMessage(e, 'Could not add calculation.');
    } finally {
      this.addingCalc = false;
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
      await Promise.all([this.loadDetail(this.selectedName), this.loadAvailable(this.selectedName), this.loadPortfolios()]);
    } catch (e) {
      console.error('removeCalc', e);
      this.detailError = this.apiErrorMessage(e, 'Could not remove calculation.');
    }
  }

  private apiErrorMessage(error: unknown, fallback: string): string {
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
    if (error.status === 0) {
      return 'Cannot reach the API. Is the backend running?';
    }
    return `${fallback} (HTTP ${error.status}).`;
  }
}
