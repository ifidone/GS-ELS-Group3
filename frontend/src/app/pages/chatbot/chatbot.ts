import { ChangeDetectorRef, Component, inject, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, timeout, TimeoutError } from 'rxjs';

import { AuthFacade } from '../../core/auth.facade';
import { environment } from '../../../environment';

/** Stop endless "Thinking…" if the server never responds (wrong port, hung OpenAI call, etc.). */
const CHAT_TIMEOUT_MS = 60_000;

type ChatSender = 'User' | 'Bot';

interface ChatMessage {
  sender: ChatSender;
  text: string;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.css',
})
export class ChatbotComponent {
  private readonly http = inject(HttpClient);
  private readonly authFacade = inject(AuthFacade);
  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly chatUrl = `${environment.apiBaseUrl}/api/chat`;

  readonly focusOptions = [
    {
      id: 'growth',
      label: 'Growth',
      prompt: 'I want to focus on growth-oriented mutual funds.',
      icon: 'growth',
    },
    {
      id: 'value',
      label: 'Value',
      prompt: 'I want to focus on value-oriented mutual funds.',
      icon: 'value',
    },
    {
      id: 'income',
      label: 'Income',
      prompt: 'I want mutual funds that emphasize income and dividends.',
      icon: 'income',
    },
    {
      id: 'fees',
      label: 'Low Fees',
      prompt: 'I want mutual funds with low expense ratios and fees.',
      icon: 'fees',
    },
  ] as const;

  readonly suggestedQuestions = [
    'Diversified selection of mutual funds',
    'How do different asset types work in a mutual fund?',
    'Explain expense ratios & fees',
  ];

  isOpen = false;
  messages: ChatMessage[] = [];
  userInput = '';
  isLoading = false;
  hideFocusOptions = false;

  toggleChat(): void {
    this.isOpen = !this.isOpen;
  }

  closeChat(): void {
    this.isOpen = false;
  }

  async sendMessage(): Promise<void> {
    await this.sendPreset(this.userInput.trim());
    this.userInput = '';
  }

  async onFocusOption(prompt: string): Promise<void> {
    this.hideFocusOptions = true;
    await this.sendPreset(prompt);
  }

  async onSuggestedQuestion(text: string): Promise<void> {
    await this.sendPreset(text);
  }

  private async sendPreset(trimmedMessage: string): Promise<void> {
    if (!trimmedMessage || this.isLoading) return;

    const user = this.authFacade.currentUser();
    if (!user?.uid) {
      this.messages.push({
        sender: 'Bot',
        text: 'Please sign in to use the chat.',
      });
      return;
    }

    this.messages.push({
      sender: 'User',
      text: trimmedMessage,
    });

    this.isLoading = true;
    try {
      const response = await firstValueFrom(
        this.http
          .post<{ reply: string }>(this.chatUrl, {
            message: trimmedMessage,
            uid: user.uid,
          })
          .pipe(timeout({ first: CHAT_TIMEOUT_MS })),
      );

      this.ngZone.run(() => {
        this.messages.push({
          sender: 'Bot',
          text: response.reply ?? 'No response.',
        });
        this.isLoading = false;
        this.cdr.detectChanges();
      });
    } catch (error) {
      console.error('Chat request failed', error);
      this.ngZone.run(() => {
        this.messages.push({
          sender: 'Bot',
          text: this.chatErrorMessage(error),
        });
        this.isLoading = false;
        this.cdr.detectChanges();
      });
    }
  }

  private chatErrorMessage(error: unknown): string {
    if (error instanceof TimeoutError || (error as Error)?.name === 'TimeoutError') {
      return (
        'No reply after ' +
        CHAT_TIMEOUT_MS / 1000 +
        's. Is the backend running with OPENAI_API_KEY set? Check the terminal where you ran ./mvnw — look for errors.'
      );
    }
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return (
          'Cannot reach the API (Failed to fetch). Start Spring Boot on port 8080 ' +
          '(backend/backend: ./mvnw spring-boot:run), restart `ng serve`, then try again. ' +
          'With `apiBaseUrl` empty, traffic goes to /api via proxy.conf.json.'
        );
      }
      if (error.status === 400) {
        const r = error.error as { reply?: string } | undefined;
        return r?.reply ?? 'Invalid chat request (empty message or missing user id). Try signing out and back in.';
      }
      if (error.status >= 500) {
        const b = error.error;
        let detail = '';
        if (b && typeof b === 'object') {
          const o = b as { reply?: string; message?: string; detail?: string; title?: string };
          detail = o.reply ?? o.detail ?? o.message ?? o.title ?? '';
        } else if (typeof b === 'string') {
          detail = b;
        }
        return (
          'Server error (HTTP ' +
          error.status +
          '). Restart the backend after pulling the latest code. If it still fails, check the backend terminal stack trace. ' +
          (detail ? 'Server said: ' + detail.slice(0, 500) : '')
        );
      }
      return error.error?.message ?? error.message ?? 'Request failed.';
    }
    return 'Sorry, something went wrong.';
  }
}
