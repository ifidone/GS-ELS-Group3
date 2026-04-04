import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthFacade } from './core/auth.facade';
import { ChatbotComponent } from './pages/chatbot/chatbot';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ChatbotComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly authFacade = inject(AuthFacade);
  private readonly router = inject(Router);

  protected readonly title = signal('frontend');

  /** Floating chat is for the app after sign-in; hide on the public landing page. */
  readonly showChatbot = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.chatbotVisibleForUrl(this.router.url)),
    ),
    { initialValue: this.chatbotVisibleForUrl(this.router.url) },
  );

  constructor() {
    this.authFacade;
  }

  private chatbotVisibleForUrl(url: string): boolean {
    const path = url.split('?')[0];
    return path !== '/' && path !== '';
  }
}
