import { Component, OnDestroy, OnInit, signal, computed, effect, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Client, IMessage } from '@stomp/stompjs';
import { GameApiService } from 'api';
import { RealtimeService } from 'realtime';
import { LocalVideoPlayerComponent, YoutubeVideoPlayerComponent, formatAppVersion } from 'ui';

@Component({
  selector: 'app-tv-lobby',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LocalVideoPlayerComponent, YoutubeVideoPlayerComponent],
  templateUrl: './tv-lobby.html',
  styleUrl: './tv-lobby.scss',
})
export class TvLobbyComponent implements OnInit, OnDestroy {
  readonly appVersion = formatAppVersion();
  gameId: string | null = null;
  joinCode: string | null = null;
  displayToken: string | null = null;
  joinCodeDisplay = '';

  pairingCode = signal<string | null>(null);
  pairingCodeDisplay = signal('');

  private pairingClient: Client | null = null;
  private pairingPollHandle: ReturnType<typeof setInterval> | null = null;
  loaded = signal(false);
  error = signal('');
  secondsLeft = signal(0);
  judgingSecondsLeft = signal(0);
  judgingPct = signal(100);
  judgingGraceSecondsLeft = signal(0);
  localVideoFailedForPerfId = signal<number | null>(null);

  private _resultPageIndex = signal(0);
  readonly resultPageIndex = this._resultPageIndex.asReadonly();

  readonly resultPageIndicators = computed(() => {
    const total = this.rt.ranking$().length;
    return Array.from({ length: Math.ceil(total / 5) });
  });

  readonly resultPage = computed(() => {
    const all = this.rt.ranking$();
    const start = this._resultPageIndex() * 5;
    return all.slice(start, start + 5);
  });

  readonly twoColRanking = computed(() => this.rt.ranking$().length > 6);
  readonly rankRows = computed(() => Math.ceil(this.rt.ranking$().length / 2));
  readonly twoColPlayers = computed(() => this.rt.players$().length > 6);
  readonly playerRows = computed(() => Math.ceil(this.rt.players$().length / 2));
  readonly wideSidebar = computed(() =>
    this.twoColRanking() || (!this.rt.currentPerformance$() && this.twoColPlayers()),
  );

  private resnapSub: any = null;
  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private resultPageHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    public rt: RealtimeService,
    private route: ActivatedRoute,
    private api: GameApiService,
  ) {
    effect(() => {
      if (this.rt.gameEnded$()) {
        // Stop all polling and reconnect attempts once game is over
        if (this.pairingPollHandle) { clearInterval(this.pairingPollHandle); this.pairingPollHandle = null; }
        this.rt.disconnect();
      }
    });
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.joinCode = params['jc'] ?? null;
      this.gameId = params['gid'] ?? null; // legacy fallback
      this.displayToken = params['dt'] ?? null;
      if ((this.joinCode || this.gameId) && this.displayToken) {
        this._loadAndConnect();
      } else {
        // TV-first: register to get a pairing code, then wait for TV_READY
        this._startPairing();
      }
    });

    this.timerHandle = setInterval(() => {
      const perf = this.rt.currentPerformance$();
      if (perf?.confirmDeadlineAt) {
        const ms = new Date(perf.confirmDeadlineAt).getTime() - Date.now();
        this.secondsLeft.set(Math.max(0, Math.ceil(ms / 1000)));
      } else {
        this.secondsLeft.set(0);
      }

      if (perf?.state === 'RUNNING' && perf.startedAt && perf.judgingDeadlineAt && perf.durationSeconds) {
        const now = Date.now();
        const songEndMs = new Date(perf.startedAt).getTime() + perf.durationSeconds * 1000;
        const deadlineMs = new Date(perf.judgingDeadlineAt).getTime();
        const songRemaining = Math.max(0, songEndMs - now);
        this.judgingSecondsLeft.set(Math.ceil(songRemaining / 1000));
        this.judgingPct.set(Math.max(0, Math.min(100, (songRemaining / (perf.durationSeconds * 1000)) * 100)));
        this.judgingGraceSecondsLeft.set(songRemaining > 0 ? 0 : Math.ceil(Math.max(0, deadlineMs - now) / 1000));
      } else {
        this.judgingSecondsLeft.set(0);
        this.judgingPct.set(0);
        this.judgingGraceSecondsLeft.set(0);
      }
    }, 500);

    this.resultPageHandle = setInterval(() => {
      const total = this.rt.ranking$().length;
      const pages = Math.ceil(total / 5);
      if (pages > 1) {
        this._resultPageIndex.update(i => (i + 1) % pages);
      }
    }, 5000);
  }

  ngOnDestroy() {
    this.resnapSub?.unsubscribe();
    if (this.timerHandle) clearInterval(this.timerHandle);
    if (this.resultPageHandle) clearInterval(this.resultPageHandle);
    if (this.pairingPollHandle) clearInterval(this.pairingPollHandle);
    this.pairingClient?.deactivate();
    this.rt.disconnect();
  }

  private _startPairing() {
    this.api.registerTv().subscribe({
      next: (reg) => {
        this.displayToken = reg.displayToken;
        this.pairingCode.set(reg.joinCode);
        this.pairingCodeDisplay.set(reg.joinCodeDisplay);

        const adopt = (joinCode: string, gameId: string) => {
          if (this.pairingCode() === null) return; // already adopted
          this.joinCode = joinCode;
          this.gameId = gameId;
          this.pairingCode.set(null);
          if (this.pairingPollHandle) { clearInterval(this.pairingPollHandle); this.pairingPollHandle = null; }
          this.pairingClient?.deactivate();
          this.pairingClient = null;
          this._loadAndConnect();
        };

        const client = new Client({
          brokerURL: `ws://${window.location.hostname}:8080/ws`,
          connectHeaders: { Authorization: `Bearer ${reg.displayToken}`, surface: 'TV' },
          reconnectDelay: 0,
          onConnect: () => {
            client.subscribe('/user/queue/tv-ready', (msg: IMessage) => {
              const data = JSON.parse(msg.body);
              adopt(data.joinCode, data.gameId);
            });
          },
        });
        client.activate();
        this.pairingClient = client;

        // Poll fallback: in case the STOMP push races with subscription setup
        this.pairingPollHandle = setInterval(() => {
          this.api.getSnapshotByCode(reg.joinCode, reg.displayToken).subscribe({
            next: (snap) => adopt(snap.joinCode, snap.gameId),
            error: () => { /* 404 = not yet created, keep waiting */ },
          });
        }, 2000);
      },
      error: () => {
        this.error.set('Could not register TV. Please refresh.');
      },
    });
  }

  private _loadAndConnect() {
    if (!this.displayToken) return;
    this.error.set('');
    const snapReq = this.joinCode
      ? this.api.getSnapshotByCode(this.joinCode, this.displayToken)
      : this.api.getSnapshot(this.gameId!, this.displayToken);

    snapReq.subscribe({
      next: (snap) => {
        this.gameId = snap.gameId;
        this.joinCodeDisplay = snap.joinCodeDisplay;
        this.rt.applySnapshot(snap);
        this.loaded.set(true);
        this.rt.connect(this.gameId!, this.displayToken!, 'TV');
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403 || err.status === 401) {
          this.error.set('Invalid display token. Ask the host for a new TV link.');
        } else {
          this.error.set('Could not load game. Please refresh.');
        }
      },
    });

    this.resnapSub = this.rt.resnap$.subscribe(() => {
      if (!this.gameId || !this.displayToken) return;
      this.api.getSnapshot(this.gameId, this.displayToken).subscribe({
        next: (snap) => this.rt.applySnapshot(snap),
      });
    });
  }

  tvMedal(rank: number): string {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
  }
}
