import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
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
  protected readonly title = signal('frontend');

  constructor() {
    this.authFacade;
  }
}
