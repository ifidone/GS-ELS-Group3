import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.html',
  styleUrl: './chatbot.css'
})
export class ChatbotComponent {

  isOpen = false;
  messages: any[] = [];
  userInput = '';

  toggleChat(){
    this.isOpen = !this.isOpen;
  }

  sendMessage(){
    if(!this.userInput) return;

    this.messages.push({
      sender: "User",
      text: this.userInput
    });

    this.messages.push({
      sender: "Bot",
      text: "This will later come from the AI backend."
    });

    this.userInput = '';
  }

}
