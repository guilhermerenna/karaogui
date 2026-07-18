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
          <label for="tvcode">TV code <span style="color:#9ca3af;font-weight:400">(optional)</span></label>
          <input id="tvcode" type="text" [(ngModel)]="tvCode"
            placeholder="Code shown on TV"
            maxlength="7"
            style="letter-spacing:.15em;text-transform:uppercase"
            (input)="tvCode = tvCode.toUpperCase()" />
          <p style="font-size:.8rem;color:#9ca3af;margin:.25rem 0 0">If your TV is already showing a code, enter it here.</p>
        </div>
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
  tvCode = '';
  loading = signal(false);
  error = signal('');

  constructor(private api: GameApiService, private router: Router) {}

  create() {
    if (!this.displayName.trim()) return;
    this.loading.set(true);
    this.error.set('');
    const code = this.tvCode.replace(/\s/g, '').trim() || undefined;
    this.api.createGame(this.displayName.trim(), code).subscribe({
      next: (res) => {
        const session: SessionInfo = {
          gameId: res.gameId,
          token: res.sessionToken,
          displayToken: res.displayToken ?? '',
          isHost: true,
          playerId: res.you.playerId,
          displayName: res.you.displayName,
        };
        sessionStorage.setItem('session', JSON.stringify(session));
        this.router.navigate(['/lobby']);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.error.set('TV code not found. Check the code on the TV screen.');
        } else {
          this.error.set('Failed to create game. Please try again.');
        }
      },
    });
  }
}
