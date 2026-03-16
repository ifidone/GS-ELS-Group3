import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthFacade } from '../../core/auth.facade';

@Component({
  selector: 'app-dashboard-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './dashboard-shell.html',
  styleUrl: './dashboard-shell.css',
})
export class DashboardShellComponent {
  private readonly router = inject(Router);
  readonly authFacade = inject(AuthFacade);

  async logout() {
    await this.authFacade.logout();
    await this.router.navigateByUrl('/auth');
  }
}
