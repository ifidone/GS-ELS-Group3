import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  Auth,
  authState,
  createUserWithEmailAndPassword,
  GoogleAuthProvider,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  User,
} from '@angular/fire/auth';
import { Firestore, doc, setDoc, serverTimestamp } from '@angular/fire/firestore';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './calculator.html',
  styleUrl: './calculator.css',
})
export class CalculatorComponent {
  private auth = inject(Auth);
  private firestore = inject(Firestore);

  currentUser = signal<User | null>(null);

  constructor() {
    authState(this.auth).subscribe((user) => {
      this.currentUser.set(user);
      this.showAuthModal = !user;
      if (user) {
        this.upsertUserDoc(user);
      }
    });
  }

  private async upsertUserDoc(user: User) {
    try {
      await setDoc(
        doc(this.firestore, 'users', user.uid),
        {
          email: user.email ?? null,
          displayName: user.displayName ?? null,
          photoURL: user.photoURL ?? null,
          lastLoginAt: serverTimestamp(),
        },
        { merge: true }
      );
    } catch (e) {
      console.error('Failed to store user data', e);
    }
  }

  funds = [
    { name: 'Vanguard 500 Index', ticker: 'VFIAX' },
    { name: 'Fidelity Growth Fund', ticker: 'FDGRX' },
    { name: 'Schwab S&P 500 Index', ticker: 'SWPPX' }
  ];

  result: any = null;

  showAuthModal = true;
  authMode: 'login' | 'signup' = 'login';
  authErrorMessage = '';

  loginEmail = '';
  loginPassword = '';
  signupName = '';
  signupEmail = '';
  signupPassword = '';

  openAuth(mode: 'login' | 'signup') {
    this.authMode = mode;
    this.showAuthModal = true;
  }

  closeAuth() {
    this.showAuthModal = false;
  }

  toggleAuthMode() {
    this.authMode = this.authMode === 'login' ? 'signup' : 'login';
  }

  async submitAuth() {
    this.authErrorMessage = '';

    if (this.authMode === 'login') {
      if (!this.loginEmail || !this.loginPassword) {
        this.authErrorMessage = 'Enter your email and password.';
        return;
      }
    } else {
      if (!this.signupEmail || !this.signupPassword) {
        this.authErrorMessage = 'Enter an email and password to create your account.';
        return;
      }
    }

    try {
      if (this.authMode === 'login') {
        await signInWithEmailAndPassword(this.auth, this.loginEmail, this.loginPassword);
      } else {
        await createUserWithEmailAndPassword(this.auth, this.signupEmail, this.signupPassword);
      }
      this.showAuthModal = false;
    } catch (error) {
      console.error('Auth error', error);
      const code = (error as { code?: string }).code || '';
      if (code === 'auth/email-already-in-use') {
        this.authErrorMessage = 'An account already exists for this email. Try logging in instead.';
      } else if (code === 'auth/weak-password') {
        this.authErrorMessage = 'Password must be at least 6 characters.';
      } else if (code === 'auth/invalid-email') {
        this.authErrorMessage = 'Enter a valid email address.';
      } else if (code === 'auth/user-not-found' || code === 'auth/wrong-password') {
        this.authErrorMessage = 'Email or password is incorrect.';
      } else {
        this.authErrorMessage = 'Unable to sign in right now. Please try again.';
      }
    }
  }

  async continueWithGoogle() {
    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(this.auth, provider);
      this.showAuthModal = false;
    } catch (error) {
      console.error('Google auth error', error);
    }
  }

  async logout() {
    try {
      await signOut(this.auth);
    } catch (error) {
      console.error('Logout error', error);
    }
  }

  userInitial(user: User): string {
    const name = user.displayName || user.email || '';
    return name ? name.charAt(0).toUpperCase() : '?';
  }

  calculate(ticker: string, amount: string, years: string) {
    if (!ticker || !amount || !years) return;

    const investment = Number(amount);
    const time = Number(years);

    const beta = 1.1;
    const expectedReturn = 0.08;

    const futureValue =
      investment * Math.pow(1 + expectedReturn, time);

    this.result = {
      fund: ticker,
      beta,
      expectedReturn,
      futureValue: futureValue.toFixed(2)
    };
  }
}
