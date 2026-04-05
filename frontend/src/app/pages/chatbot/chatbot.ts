import { ChangeDetectorRef, Component, effect, inject, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, timeout, TimeoutError } from 'rxjs';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { AuthFacade } from '../../core/auth.facade';
import { environment } from '../../../environment';

/** Stop endless "Thinking…" if the server never responds (wrong port, hung OpenAI call, etc.). */
const CHAT_TIMEOUT_MS = 60_000;

type ChatSender = 'User' | 'Bot';

interface ChatMessage {
  sender: ChatSender;
  text: string;
  html?: SafeHtml;
  ts?: number;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.css',
})
export class ChatbotComponent {
  private static readonly MAX_HISTORY_MESSAGES = 10;
  private readonly http = inject(HttpClient);
  private readonly authFacade = inject(AuthFacade);
  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly chatUrl = `${environment.apiBaseUrl}/api/chat`;
  private readonly historyStoragePrefix = 'chat_history_';
  private lastHistoryUid: string | null = null;

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
  private chatHistory: ChatMessage[] = [];
  userInput = '';
  isLoading = false;
  hideFocusOptions = false;

  constructor() {
    effect(() => {
      const uid = this.authFacade.currentUser()?.uid ?? null;
      if (uid === this.lastHistoryUid) {
        return;
      }
      this.lastHistoryUid = uid;
      if (!uid) {
        this.messages = [];
        this.chatHistory = [];
        return;
      }
      this.loadHistory(uid);
    });
  }

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
        html: this.formatBotReply('Please sign in to use the chat.'),
      });
      return;
    }

    this.messages.push({
      sender: 'User',
      text: trimmedMessage,
    });
    this.addHistoryEntry('User', trimmedMessage);

    this.isLoading = true;
    try {
      const history = this.getHistoryForRequest();
      const response = await firstValueFrom(
        this.http
          .post<{ reply: string }>(this.chatUrl, {
            message: trimmedMessage,
            uid: user.uid,
            history,
          })
          .pipe(timeout({ first: CHAT_TIMEOUT_MS })),
      );

      this.ngZone.run(() => {
        if (response.reply) {
          this.addHistoryEntry('Bot', response.reply);
        }
        this.messages.push({
          sender: 'Bot',
          text: response.reply ?? 'No response.',
          html: this.formatBotReply(response.reply ?? 'No response.'),
        });
        this.isLoading = false;
        this.cdr.detectChanges();
      });
    } catch (error) {
      console.error('Chat request failed', error);
      const message = this.chatErrorMessage(error);
      this.ngZone.run(() => {
        this.messages.push({
          sender: 'Bot',
          text: message,
          html: this.formatBotReply(message),
        });
        this.isLoading = false;
        this.cdr.detectChanges();
      });
    }
  }

  private loadHistory(uid: string): void {
    const raw = localStorage.getItem(this.historyStoragePrefix + uid);
    if (!raw) {
      this.chatHistory = [];
      this.messages = [];
      return;
    }
    try {
      const parsed = JSON.parse(raw) as ChatMessage[];
      if (!Array.isArray(parsed)) {
        this.chatHistory = [];
        this.messages = [];
        return;
      }
      this.chatHistory = parsed
        .filter((entry) => entry && (entry.sender === 'User' || entry.sender === 'Bot'))
        .slice(-ChatbotComponent.MAX_HISTORY_MESSAGES);
      this.messages = this.chatHistory.map((entry) => ({
        sender: entry.sender,
        text: entry.text,
        html: entry.sender === 'Bot' ? this.formatBotReply(entry.text) : undefined,
        ts: entry.ts,
      }));
    } catch {
      this.chatHistory = [];
      this.messages = [];
    }
  }

  private addHistoryEntry(sender: ChatSender, text: string): void {
    const uid = this.authFacade.currentUser()?.uid;
    if (!uid) {
      return;
    }
    const entry: ChatMessage = { sender, text, ts: Date.now() };
    this.chatHistory = [...this.chatHistory, entry].slice(-ChatbotComponent.MAX_HISTORY_MESSAGES);
    localStorage.setItem(this.historyStoragePrefix + uid, JSON.stringify(this.chatHistory));
  }

  private getHistoryForRequest(): Array<{ role: 'user' | 'assistant'; content: string }> {
    return this.chatHistory.map((entry) => ({
      role: entry.sender === 'User' ? 'user' : 'assistant',
      content: entry.text,
    }));
  }

  private formatBotReply(text: string): SafeHtml {
    const escaped = this.escapeHtml(text);
    const lines = escaped.split(/\r?\n/);
    let html = '';
    let inList: 'ol' | 'ul' | null = null;
    const closeList = () => {
      if (inList) {
        html += `</${inList}>`;
        inList = null;
      }
    };

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) {
        if (!inList) {
          html += '<div class="msg-spacer"></div>';
        }
        continue;
      }

      const orderedMatch = /^(\d+)\.\s+(.*)$/.exec(trimmed);
      if (orderedMatch) {
        if (inList !== 'ol') {
          closeList();
          html += '<ol>';
          inList = 'ol';
        }
        html += `<li>${this.inlineMarkup(orderedMatch[2])}</li>`;
        continue;
      }

      const unorderedMatch = /^[-*]\s+(.*)$/.exec(trimmed);
      if (unorderedMatch) {
        if (inList === 'ol') {
          html += `<p class="msg-subbullet">• ${this.inlineMarkup(unorderedMatch[1])}</p>`;
        } else {
          if (inList !== 'ul') {
            closeList();
            html += '<ul>';
            inList = 'ul';
          }
          html += `<li>${this.inlineMarkup(unorderedMatch[1])}</li>`;
        }
        continue;
      }

      closeList();
      html += `<p>${this.inlineMarkup(trimmed)}</p>`;
    }

    closeList();
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  private inlineMarkup(text: string): string {
    let value = text;
    value = value.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    value = value.replace(/`([^`]+?)`/g, '<code>$1</code>');
    return value;
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
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
