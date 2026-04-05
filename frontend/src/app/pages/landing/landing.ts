import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

type AssistantTopic = {
  id: string;
  chipLabel: string;
  userMessage: string;
  assistantReply: string;
};

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class LandingPageComponent {
  /** Demo replies only — full assistant after sign-in uses the real chatbot. */
  readonly assistantTopics: AssistantTopic[] = [
    {
      id: 'beta',
      chipLabel: 'Beta',
      userMessage: 'What is beta and why does it matter for my investments?',
      assistantReply:
        'Beta measures how much a fund moves relative to the overall market. A beta of 1.0 means it ' +
        'moves with the market; higher beta usually means more volatility and potential for larger ' +
        'swings up or down.',
    },
    {
      id: 'capm',
      chipLabel: 'What is CAPM?',
      userMessage: 'What is CAPM?',
      assistantReply:
        'CAPM (Capital Asset Pricing Model) relates expected return to risk. In a common form, ' +
        'expected return ≈ risk-free rate + beta × (expected market return − risk-free rate). It is a ' +
        'teaching model—real markets are more complex and this is not personalized advice.',
    },
    {
      id: 'compound',
      chipLabel: 'Compound interest',
      userMessage: 'Explain compound interest in simple terms.',
      assistantReply:
        'Compound interest means your balance can earn returns, and those returns can earn returns too, ' +
        'so growth can accelerate over long horizons. The rate, time, and assumptions (like steady ' +
        'returns) matter a lot—our calculator is for learning, not a guarantee of outcomes.',
    },
    {
      id: 'risk',
      chipLabel: 'Risk vs return',
      userMessage: 'How should I think about risk vs return?',
      assistantReply:
        'Generally, assets with higher expected return potential also carry more uncertainty or ' +
        'drawdown risk. Diversification can spread risk across holdings, but it does not eliminate it. ' +
        'Use simulations here to build intuition, not to pick investments for real money without your ' +
        'own research.',
    },
  ];

  activeAssistantTopicId = 'beta';

  scrollTo(id: string): void {
    const el = document.getElementById(id);
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  get activeAssistantTopic(): AssistantTopic {
    return (
      this.assistantTopics.find((t) => t.id === this.activeAssistantTopicId) ?? this.assistantTopics[0]
    );
  }

  selectAssistantTopic(id: string): void {
    this.activeAssistantTopicId = id;
  }
}
