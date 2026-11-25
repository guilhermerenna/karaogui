import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GameApiService } from 'api';
import type { SessionInfo } from 'contracts';

@Component({
  selector: 'app-create-game',
  imports: [FormsModule, RouterLink],
  templateUrl: './create-game.html',
  styleUrl: './create-game.scss',
})
export class CreateGameComponent {
  displayName = '';
  tvCode = '';
  loading = signal(false);
  error = signal('');

  constructor(private api: GameApiService, private router: Router) {}

  get normalizedTvCode(): string {
    return this.tvCode.replace(/\s/g, '').trim();
  }

  get canCreate(): boolean {
    return Boolean(this.displayName.trim() && this.normalizedTvCode && !this.loading());
  }

  create() {
    if (!this.displayName.trim() || !this.normalizedTvCode) return;
    this.loading.set(true);
    this.error.set('');
    this.api.createGame(this.displayName.trim(), this.normalizedTvCode).subscribe({
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
