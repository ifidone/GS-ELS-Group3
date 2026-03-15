import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import {
  Auth,
  GoogleAuthProvider,
  User,
  authState,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updateProfile,
} from '@angular/fire/auth';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environment';

@Injectable({ providedIn: 'root' })
export class AuthFacade {
  private readonly auth = inject(Auth);
  private readonly http = inject(HttpClient);
  private readonly authSyncUrl = `${environment.apiBaseUrl}/api/auth/sync`;

  readonly currentUser = signal<User | null>(null);

  constructor() {
    authState(this.auth).subscribe((user) => {
      this.currentUser.set(user);
      if (user) {
        void this.syncAuthenticatedUser(user);
      }
    });
  }

  async login(email: string, password: string) {
    await signInWithEmailAndPassword(this.auth, email, password);
  }

  async signup(name: string, email: string, password: string) {
    const credential = await createUserWithEmailAndPassword(this.auth, email, password);
    const trimmedName = name.trim();
    if (trimmedName) {
      await updateProfile(credential.user, { displayName: trimmedName });
      this.currentUser.set(this.auth.currentUser);
    }
  }

  async continueWithGoogle() {
    const provider = new GoogleAuthProvider();
    await signInWithPopup(this.auth, provider);
  }

  async logout() {
    await signOut(this.auth);
  }

  userInitial(user: User): string {
    const name = user.displayName || user.email || '';
    return name ? name.charAt(0).toUpperCase() : '?';
  }

  private async syncAuthenticatedUser(user: User) {
    try {
      const token = await user.getIdToken();
      await firstValueFrom(
        this.http.post(
          this.authSyncUrl,
          { uid: user.uid },
          {
            headers: new HttpHeaders({
              Authorization: `Bearer ${token}`,
            }),
          }
        )
      );
    } catch (error) {
      console.error('Failed to sync authenticated user', error);
    }
  }
}
