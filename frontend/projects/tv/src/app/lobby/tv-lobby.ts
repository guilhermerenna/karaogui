import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { GameApiService } from 'api';
import type { GameSnapshotDto } from 'contracts';

@Component({
  selector: 'app-tv-lobby',
  imports: [],
  template: `
    <div class="tv-page">
      @if (!gameId) {
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle">Waiting for host to share the TV link</div>
        </div>
      } @else if (error()) {
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle" style="color:#f87171">{{ error() }}</div>
        </div>
      } @else if (!snapshot()) {
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle">Loading…</div>
        </div>
      } @else {
        <div class="tv-content">
          <div class="tv-left">
            <div class="tv-label">Join code</div>
            <div class="tv-join-code">{{ snapshot()!.joinCodeDisplay }}</div>
            <div class="tv-state-badge" [class.active]="snapshot()!.state === 'ACTIVE'">
              {{ snapshot()!.state === 'ACTIVE' ? 'Game in progress' : 'Waiting for host to start' }}
            </div>
            <button class="tv-refresh" (click)="refresh()">Refresh</button>
          </div>
          <div class="tv-right">
            <div class="tv-label">Players ({{ snapshot()!.players.length }})</div>
            <ul class="tv-player-list">
              @for (p of snapshot()!.players; track p.playerId) {
                <li class="tv-player">
                  <span class="tv-player-name">{{ p.displayName }}</span>
                  @if (p.isHost) { <span class="tv-host-badge">host</span> }
                  <span class="tv-player-score">{{ p.score }} pts</span>
                </li>
              }
            </ul>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .tv-page {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem;
    }
    .tv-waiting {
      text-align: center;
    }
    .tv-title {
      font-size: 4rem;
      font-weight: 800;
      color: #a5b4fc;
      margin-bottom: 1rem;
    }
    .tv-subtitle {
      font-size: 1.5rem;
      color: #9ca3af;
    }
    .tv-content {
      display: flex;
      gap: 5rem;
      align-items: flex-start;
      width: 100%;
      max-width: 1400px;
    }
    .tv-left {
      flex-shrink: 0;
    }
    .tv-label {
      font-size: 1rem;
      text-transform: uppercase;
      letter-spacing: .15em;
      color: #6b7280;
      margin-bottom: .5rem;
    }
    .tv-join-code {
      font-size: 6rem;
      font-weight: 900;
      letter-spacing: .5em;
      color: #a5b4fc;
      line-height: 1;
      margin-bottom: 1.5rem;
    }
    .tv-state-badge {
      display: inline-block;
      padding: .5rem 1.25rem;
      border-radius: 2rem;
      background: #1e293b;
      color: #94a3b8;
      font-size: 1rem;
      margin-bottom: 1.5rem;
    }
    .tv-state-badge.active {
      background: #14532d;
      color: #86efac;
    }
    .tv-refresh {
      display: block;
      padding: .625rem 1.5rem;
      border: 1.5px solid #374151;
      border-radius: 8px;
      background: transparent;
      color: #9ca3af;
      font-size: 1rem;
      cursor: pointer;
    }
    .tv-refresh:hover { background: #1e293b; }
    .tv-right {
      flex: 1;
    }
    .tv-player-list {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .tv-player {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1rem 0;
      border-bottom: 1px solid #1e293b;
      font-size: 1.5rem;
    }
    .tv-player-name {
      flex: 1;
      font-weight: 600;
    }
    .tv-host-badge {
      font-size: .875rem;
      background: #6366f1;
      color: #fff;
      border-radius: 4px;
      padding: .15rem .5rem;
    }
    .tv-player-score {
      font-size: 1.25rem;
      color: #6b7280;
    }
  `],
})
export class TvLobbyComponent implements OnInit {
  gameId: string | null = null;
  displayToken: string | null = null;
  snapshot = signal<GameSnapshotDto | null>(null);
  error = signal('');

  constructor(private route: ActivatedRoute, private api: GameApiService) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.gameId = params['gid'] ?? null;
      this.displayToken = params['dt'] ?? null;
      if (this.gameId && this.displayToken) {
        this.refresh();
      }
    });
  }

  refresh() {
    if (!this.gameId || !this.displayToken) return;
    this.error.set('');
    this.api.getSnapshot(this.gameId, this.displayToken).subscribe({
      next: (snap) => this.snapshot.set(snap),
      error: (err: HttpErrorResponse) => {
        if (err.status === 403 || err.status === 401) {
          this.error.set('Invalid display token. Ask the host for a new TV link.');
        } else {
          this.error.set('Could not load game. Please refresh.');
        }
      },
    });
  }
}
