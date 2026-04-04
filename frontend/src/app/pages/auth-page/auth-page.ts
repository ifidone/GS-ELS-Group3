import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthFacade } from '../../core/auth.facade';

@Component({
  selector: 'app-auth-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth-page.html',
  styleUrl: './auth-page.css',
})
export class AuthPageComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly authFacade = inject(AuthFacade);

  authMode: 'login' | 'signup' = 'login';
  authErrorMessage = '';

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      const mode = params.get('mode');
      if (mode === 'signup') {
        this.authMode = 'signup';
      } else if (mode === 'login') {
        this.authMode = 'login';
      }
    });
  }

  loginEmail = '';
  loginPassword = '';
  signupName = '';
  signupEmail = '';
  signupPassword = '';

  openAuth(mode: 'login' | 'signup') {
    this.authMode = mode;
    this.authErrorMessage = '';
  }

  async submitAuth() {
    this.authErrorMessage = '';

    if (this.authMode === 'login') {
      if (!this.loginEmail || !this.loginPassword) {
        this.authErrorMessage = 'Enter your email and password.';
        return;
      }
    } else if (!this.signupEmail || !this.signupPassword) {
      this.authErrorMessage = 'Enter an email and password to create your account.';
      return;
    }

    try {
      if (this.authMode === 'login') {
        await this.authFacade.login(this.loginEmail, this.loginPassword);
      } else {
        await this.authFacade.signup(this.signupName, this.signupEmail, this.signupPassword);
      }

      await this.router.navigateByUrl('/dashboard');
    } catch (error) {
      console.error('Auth error', error);
      this.authErrorMessage = this.getErrorMessage(error);
    }
  }

  async continueWithGoogle() {
    this.authErrorMessage = '';

    try {
      await this.authFacade.continueWithGoogle();
      await this.router.navigateByUrl('/dashboard');
    } catch (error) {
      console.error('Google auth error', error);
      this.authErrorMessage = this.getErrorMessage(error);
    }
  }

  private getErrorMessage(error: unknown): string {
    const code = (error as { code?: string }).code || '';
    if (code === 'auth/email-already-in-use') {
      return 'An account already exists for this email. Try logging in instead.';
    }
    if (code === 'auth/weak-password') {
      return 'Password must be at least 6 characters.';
    }
    if (code === 'auth/invalid-email') {
      return 'Enter a valid email address.';
    }
    if (
      code === 'auth/user-not-found' ||
      code === 'auth/wrong-password' ||
      code === 'auth/invalid-credential'
    ) {
      return 'Email or password is incorrect.';
    }
    if (code === 'auth/popup-closed-by-user') {
      return 'Google sign-in was cancelled before completion.';
    }
    return 'Unable to authenticate right now. Please try again.';
  }
}
