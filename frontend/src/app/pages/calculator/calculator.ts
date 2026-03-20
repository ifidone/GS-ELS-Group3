import { Component, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
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

@Component({
  selector: 'app-calculator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './calculator.html',
  styleUrl: './calculator.css',
})
export class CalculatorComponent implements OnInit {
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

  result: any = null;
  history: SavedCalculation[] = [];
  historyError = '';

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

  async calculate(ticker: string, amount: string, years: string) {
    if (!ticker || !amount || !years) return;

    const investment = Number(amount);
    const time = Number(years);
    if (!Number.isFinite(investment) || !Number.isFinite(time)) return;
    if (investment <= 0 || time <= 0) return;

    try {
      const response = await firstValueFrom(
        this.http.post<CalculatorProjectionResponse>(this.calculatorApiUrl, {
          ticker,
          initialInvestment: investment,
          years: time,
        })
      );

      this.result = {
        fund: response.ticker,
        beta: response.beta,
        expectedReturn: response.expectedReturn,
        futureValue: response.futureValue.toFixed(2),
      };
      void this.saveCalculation(response);
    } catch (error) {
      console.error('Calculator API error', error);
    }
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
    } catch (error) {
      console.error('Failed to load calculation history', error);
      this.historyError = 'Unable to load saved calculations.';
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
}
