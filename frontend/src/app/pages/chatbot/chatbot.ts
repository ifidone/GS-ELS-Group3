import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AuthFacade } from '../../core/auth.facade';
import { environment } from '../../../environment';

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.css'
})
export class ChatbotComponent {
  private readonly http = inject(HttpClient);
  private readonly authFacade = inject(AuthFacade);
  private readonly chatUrl = `${environment.apiBaseUrl}/api/chat`;

  isOpen = false;
  messages: any[] = [];
  userInput = '';
  isLoading = false;

  toggleChat(){
    this.isOpen = !this.isOpen;
  }

  async sendMessage(){
    const trimmedMessage = this.userInput.trim();
    if(!trimmedMessage || this.isLoading) return;

    const user = this.authFacade.currentUser();
    if (!user?.uid) {
      this.messages.push({
        sender: "Bot",
        text: "Please sign in to use the chat."
      });
      this.userInput = '';
      return;
    }

    this.messages.push({
      sender: "User",
      text: trimmedMessage
    });

    this.isLoading = true;
    try {
      const response = await firstValueFrom(
        this.http.post<{ reply: string }>(this.chatUrl, {
          message: trimmedMessage,
          uid: user.uid
        })
      );

      this.messages.push({
        sender: "Bot",
        text: response.reply ?? 'No response.'
      });
    } catch (error) {
      console.error('Chat request failed', error);
      this.messages.push({
        sender: "Bot",
        text: "Sorry, something went wrong."
      });
    } finally {
      this.isLoading = false;
    }

    this.userInput = '';
  }

}
