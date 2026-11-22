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
  templateUrl: './lobby.html',
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
