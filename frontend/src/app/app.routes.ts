import { Routes } from '@angular/router';
import { authGuard, publicOnlyGuard } from './core/auth.guards';
import { AuthPageComponent } from './pages/auth-page/auth-page';
import { CalculatorComponent } from './pages/calculator/calculator';
import { DashboardHomeComponent } from './pages/dashboard-home/dashboard-home';
import { DashboardShellComponent } from './pages/dashboard-shell/dashboard-shell';

export const routes: Routes = [
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
    ],
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard',
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
