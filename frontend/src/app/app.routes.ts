import { Routes } from '@angular/router';
import { authGuard, publicOnlyGuard } from './core/auth.guards';
import { AuthPageComponent } from './pages/auth-page/auth-page';
import { CalculatorComponent } from './pages/calculator/calculator';
import { DashboardHomeComponent } from './pages/dashboard-home/dashboard-home';
import { PortfoliosComponent } from './pages/portfolios/portfolios';
import { DashboardShellComponent } from './pages/dashboard-shell/dashboard-shell';
import { LandingPageComponent } from './pages/landing/landing';

export const routes: Routes = [
  {
    path: '',
    component: LandingPageComponent,
    canActivate: [publicOnlyGuard],
  },
  {
    path: 'auth',
    component: AuthPageComponent,
    canActivate: [publicOnlyGuard],
  },
  {
    path: 'dashboard',
    component: DashboardShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        component: DashboardHomeComponent,
      },
      {
        path: 'calculator',
        component: CalculatorComponent,
      },
      {
        path: 'portfolios',
        component: PortfoliosComponent,
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
