import { Component, OnDestroy, OnInit, signal, effect, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { GameApiService } from 'api';
import { RealtimeService } from 'realtime';
import type { SessionInfo } from 'contracts';

@Component({
  selector: 'app-lobby',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <div class="page">
      <div class="card" style="max-width:480px">
        @if (!session) {
          <p class="error-msg">No session found. <a href="/">Go back</a></p>
        } @else {
          <div style="margin-bottom:1.5rem">
            <div style="font-size:.75rem;color:#888;margin-bottom:.25rem">Join code</div>
            <div style="font-size:2.5rem;font-weight:800;letter-spacing:.3em;color:#6366f1">
              {{ joinCodeDisplay }}
            </div>
          </div>

          <div style="margin-bottom:1.5rem">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.75rem">
              <h2 style="margin:0">Players ({{ rt.players$().length }})</h2>
            </div>
            @if (rt.players$().length) {
              <ul style="list-style:none;margin:0;padding:0">
                @for (p of rt.players$(); track p.playerId) {
                  <li style="display:flex;align-items:center;padding:.5rem 0;border-bottom:1px solid #f0f0f0">
                    <span style="flex:1">{{ p.displayName }}</span>
                    @if (p.isHost) { <span style="font-size:.75rem;background:#6366f1;color:#fff;border-radius:4px;padding:.1rem .4rem">host</span> }
                  </li>
                }
              </ul>
            } @else {
              <p style="color:#888;font-size:.9rem">Waiting for players to join…</p>
            }
          </div>

          @if (session.isHost && rt.gameState$() === 'CREATED') {
            <button class="btn btn-primary" style="margin-bottom:1rem"
              [disabled]="starting() || rt.players$().length < 3" (click)="startGame()">
              {{ starting() ? 'Starting…' : 'Start game' }}
            </button>
            @if (rt.players$().length < 3) {
              <p style="color:#888;font-size:.85rem;margin:-.5rem 0 1rem">
                Need at least 3 players to start ({{ rt.players$().length }}/3).
              </p>
            }
          }

          @if (error()) {
            <p class="error-msg">{{ error() }}</p>
          }
        }
      </div>
    </div>
  `,
})
export class LobbyComponent implements OnInit, OnDestroy {
  session: SessionInfo | null = null;
  joinCodeDisplay = '';
  joinCode = '';
  starting = signal(false);
  error = signal('');

  private resnapSub: any = null;

  constructor(public rt: RealtimeService, private api: GameApiService, private router: Router) {
    effect(() => {
      if (this.rt.gameState$() === 'ACTIVE') {
        this.router.navigate(['/performance']);
      }
    });
  }

  ngOnInit() {
    const raw = sessionStorage.getItem('session');
    if (!raw) return;
    this.session = JSON.parse(raw) as SessionInfo;

    this.api.getSnapshot(this.session.gameId, this.session.token).subscribe({
      next: (snap) => {
        this.joinCodeDisplay = snap.joinCodeDisplay;
        this.joinCode = snap.joinCode;
        this.rt.applySnapshot(snap);
        this.rt.connect(this.session!.gameId, this.session!.token, 'PHONE');
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          sessionStorage.removeItem('session');
          this.router.navigate(['/']);
        }
      },
    });

    this.resnapSub = this.rt.resnap$.subscribe(() => {
      if (!this.session) return;
      this.api.getSnapshot(this.session.gameId, this.session.token).subscribe({
        next: (snap) => this.rt.applySnapshot(snap),
      });
    });
  }

  ngOnDestroy() {
    this.resnapSub?.unsubscribe();
    this.rt.disconnect();
  }

  startGame() {
    if (!this.session) return;
    this.starting.set(true);
    this.error.set('');
    this.api.startGame(this.session.gameId, this.session.token).subscribe({
      next: (snap) => {
        this.rt.applySnapshot(snap);
        this.starting.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.starting.set(false);
        const code = err.error?.error?.code;
        if (code === 'NOT_ENOUGH_PLAYERS') {
          this.error.set('Need at least 3 players to start.');
        } else {
          this.error.set(err.status === 409 ? 'Game already started.' : 'Could not start game.');
        }
      },
    });
  }
}
