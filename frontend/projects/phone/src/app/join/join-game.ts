import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { GameApiService } from 'api';
import type { SessionInfo } from 'contracts';

@Component({
  selector: 'app-join-game',
  imports: [FormsModule, RouterLink],
  templateUrl: './join-game.html',
  styleUrl: './join-game.scss',
})
export class JoinGameComponent {
  joinCode = '';
  displayName = '';
  loading = signal(false);
  error = signal('');

  constructor(private api: GameApiService, private router: Router) {}

  formatCode(event: Event) {
    const input = event.target as HTMLInputElement;
    input.value = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
    this.joinCode = input.value;
  }

  canSubmit(): boolean {
    return this.joinCode.trim().length === 6 && this.displayName.trim().length > 0;
  }

  join() {
    if (!this.canSubmit()) return;
    this.loading.set(true);
    this.error.set('');
    this.api.joinGame(this.joinCode.trim(), this.displayName.trim()).subscribe({
      next: (res) => {
        const session: SessionInfo = {
          gameId: res.gameId,
          token: res.sessionToken,
          displayToken: res.displayToken,
          isHost: false,
          playerId: res.you.playerId,
          displayName: res.you.displayName,
        };
        sessionStorage.setItem('session', JSON.stringify(session));
        this.router.navigate(['/lobby']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.error.set('Game not found. Check the code and try again.');
        } else if (err.status === 409) {
          this.error.set('This game has already started.');
        } else {
          this.error.set('Failed to join. Please try again.');
        }
      },
    });
  }
}
