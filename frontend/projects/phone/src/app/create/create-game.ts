import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GameApiService } from 'api';
import type { SessionInfo } from 'contracts';

@Component({
  selector: 'app-create-game',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="card">
        <a routerLink="/" class="back-link">← Back</a>
        <h2>Create a game</h2>
        <div class="field">
          <label for="name">Your display name</label>
          <input id="name" type="text" [(ngModel)]="displayName" placeholder="e.g. DJ Soprano" maxlength="50" />
        </div>
        <button class="btn btn-primary" [disabled]="!displayName.trim() || loading()" (click)="create()">
          {{ loading() ? 'Creating…' : 'Create game' }}
        </button>
        @if (error()) {
          <p class="error-msg">{{ error() }}</p>
        }
      </div>
    </div>
  `,
})
export class CreateGameComponent {
  displayName = '';
  loading = signal(false);
  error = signal('');

  constructor(private api: GameApiService, private router: Router) {}

  create() {
    if (!this.displayName.trim()) return;
    this.loading.set(true);
    this.error.set('');
    this.api.createGame(this.displayName.trim()).subscribe({
      next: (res) => {
        const session: SessionInfo = {
          gameId: res.gameId,
          token: res.sessionToken,
          displayToken: res.displayToken,
          isHost: true,
          playerId: res.you.playerId,
          displayName: res.you.displayName,
        };
        sessionStorage.setItem('session', JSON.stringify(session));
        this.router.navigate(['/lobby']);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to create game. Please try again.');
      },
    });
  }
}
