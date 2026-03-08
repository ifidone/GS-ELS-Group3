import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
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
import { Firestore, addDoc, collection, limit, doc, onSnapshot, orderBy, query, setDoc, serverTimestamp } from '@angular/fire/firestore';
import { environment } from '../../../environment';

type ProjectionHistoryItem = {
  id: string;
  fund: string;
  beta: number;
  expectedReturn: number;
  futureValue: string;
  investment: number;
  years: number;
  createdAt: Date | null;
};

type CalculatorProjectionResponse = {
  ticker: string;
  initialInvestment: number;
  years: number;
  beta: number;
  expectedReturn: number;
  futureValue: number;
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './calculator.html',
  styleUrl: './calculator.css',
})
export class CalculatorComponent implements OnDestroy {
  private auth = inject(Auth);
  private firestore = inject(Firestore);
  private http = inject(HttpClient);
  private readonly calculatorApiUrl = `${environment.apiBaseUrl}/api/calculator/project`;

  currentUser = signal<User | null>(null);
  history = signal<ProjectionHistoryItem[]>([]);

  private unsubscribeHistory: (() => void) | null = null;

  constructor() {
    authState(this.auth).subscribe((user) => {
      this.currentUser.set(user);
      this.showAuthModal = !user;
      if (user) {
        this.upsertUserDoc(user);
        this.startHistoryListener(user.uid);
      } else {
        this.stopHistoryListener();
        this.history.set([]);
      }
    });
  }

  ngOnDestroy(): void {
    this.stopHistoryListener();
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

  private stopHistoryListener() {
    if (this.unsubscribeHistory) {
      this.unsubscribeHistory();
      this.unsubscribeHistory = null;
    }
  }

  private startHistoryListener(uid: string) {
    this.stopHistoryListener();

    const historyRef = collection(this.firestore, 'users', uid, 'projectionHistory');
    const historyQuery = query(historyRef, orderBy('createdAt', 'desc'), limit(20));

    this.unsubscribeHistory = onSnapshot(historyQuery, (snapshot) => {
      const rows: ProjectionHistoryItem[] = snapshot.docs.map((docSnap) => {
        const data = docSnap.data() as any;
        return {
          id: docSnap.id,
          fund: data.fund,
          beta: data.beta,
          expectedReturn: data.expectedReturn,
          futureValue: data.futureValue,
          investment: data.investment,
          years: data.years,
          createdAt: data.createdAt?.toDate?.() ?? null,
        };
      });

      this.history.set(rows);
    });
  }

  private async saveProjectionToHistory(
    uid: string,
    payload: {
      fund: string;
      beta: number;
      expectedReturn: number;
      futureValue: string;
      investment: number;
      years: number;
    }
  ) {
    try {
      await addDoc(collection(this.firestore, 'users', uid, 'projectionHistory'), {
        ...payload,
        createdAt: serverTimestamp(),
      });
    } catch (e) {
      console.error('Failed to save projection history', e);
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
      const user = this.currentUser();
      if (user) {
        void this.saveProjectionToHistory(user.uid, {
          fund: response.ticker,
          beta: response.beta,
          expectedReturn: response.expectedReturn,
          futureValue: response.futureValue.toFixed(2),
          investment,
          years: time,
        });
      }
    } catch (error) {
      console.error('Calculator API error', error);
    }
  }
}
