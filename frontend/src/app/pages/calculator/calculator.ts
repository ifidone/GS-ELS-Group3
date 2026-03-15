import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environment';

type CalculatorProjectionResponse = {
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
};

@Component({
  selector: 'app-calculator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './calculator.html',
  styleUrl: './calculator.css',
})
export class CalculatorComponent {
  private http = inject(HttpClient);
  private readonly calculatorApiUrl = `${environment.apiBaseUrl}/api/calculator/project`;

  funds = [
    { name: 'Vanguard 500 Index', ticker: 'VFIAX' },
    { name: 'Fidelity Growth Fund', ticker: 'FDGRX' },
    { name: 'Schwab S&P 500 Index', ticker: 'SWPPX' },
  ];

  result: any = null;

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
    } catch (error) {
      console.error('Calculator API error', error);
    }
  }
}
