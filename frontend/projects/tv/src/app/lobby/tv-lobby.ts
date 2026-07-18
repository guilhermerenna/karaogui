import { Component, OnDestroy, OnInit, signal, computed, effect, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Client, IMessage } from '@stomp/stompjs';
import { GameApiService } from 'api';
import { RealtimeService } from 'realtime';

@Component({
  selector: 'app-tv-lobby',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <div class="tv-page">
      @if (pairingCode()) {
        <!-- TV-first pairing: show the code, wait for host to create game -->
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle" style="margin-bottom:2rem">Enter this code when creating the game</div>
          <div class="tv-join-code" style="font-size:6rem;letter-spacing:.5em;color:#a5b4fc;font-weight:900">
            {{ pairingCodeDisplay() }}
          </div>
          <div style="margin-top:2rem;font-size:1rem;color:#6b7280">Waiting for host…</div>
        </div>
      } @else if (!gameId && !joinCode) {
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle">Loading…</div>
        </div>
      } @else if (error()) {
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle" style="color:#f87171">{{ error() }}</div>
        </div>
      } @else if (!loaded()) {
        <div class="tv-waiting">
          <div class="tv-title">KaraoGUI</div>
          <div class="tv-subtitle">Loading…</div>
        </div>
      } @else {
        <div class="tv-content">

          <!-- GAME OVER OVERLAY -->
          @if (rt.gameEnded$()) {
            <div style="position:fixed;inset:0;background:#0f172a;display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:10">
              <div style="font-size:5rem;font-weight:900;color:#a5b4fc;margin-bottom:.5rem">Game Over</div>
              <div style="font-size:1.5rem;color:#6b7280;margin-bottom:3rem">Final Standings</div>

              <div style="width:100%;max-width:700px">
                @for (entry of resultPage(); track entry.rank) {
                  <div style="display:flex;align-items:center;gap:1.5rem;padding:1rem 1.5rem;border-radius:12px;margin-bottom:.75rem"
                    [style.background]="entry.rank === 1 ? '#713f12' : entry.rank === 2 ? '#1e293b' : entry.rank === 3 ? '#422006' : '#0f172a'">
                    <span style="font-size:2.5rem;min-width:3rem;text-align:center">{{ tvMedal(entry.rank) }}</span>
                    <span style="flex:1;font-size:2rem;font-weight:700;color:#e2e8f0">{{ entry.displayName }}</span>
                    <span style="font-size:2.5rem;font-weight:900;color:#a5b4fc">{{ entry.score }} pts</span>
                  </div>
                }
              </div>

              <div style="display:flex;gap:.75rem;margin-top:2rem">
                @for (p of resultPageIndicators(); track $index) {
                  <div style="width:10px;height:10px;border-radius:50%"
                    [style.background]="$index === resultPageIndex() ? '#a5b4fc' : '#334155'"></div>
                }
              </div>
            </div>
          }

          <!-- LEFT PANEL: join code + slot status -->
          <div class="tv-left">
            <div class="tv-label">Join code</div>
            <div class="tv-join-code">{{ joinCodeDisplay }}</div>

            @if (rt.currentPerformance$()) {
              @let perf = rt.currentPerformance$()!;
              <div class="tv-label" style="margin-bottom:.75rem">Performers</div>
              <div style="display:flex;flex-direction:column;gap:.5rem">
                @for (slot of perf.slots; track slot.slotId) {
                  <div class="tv-slot-card"
                    [class.confirmed]="slot.state === 'CONFIRMED' || slot.state === 'REPLACED'"
                    [class.pending]="slot.state === 'PENDING'">
                    <span class="tv-slot-name">{{ slot.currentPlayerName }}</span>
                    <span class="tv-slot-badge">{{ slot.state }}</span>
                  </div>
                }
              </div>
            } @else {
              <div class="tv-state-badge" [class.active]="rt.gameState$() === 'ACTIVE'">
                {{ rt.gameState$() === 'ACTIVE' ? 'Game in progress' : 'Waiting for host to start' }}
              </div>
              <div class="tv-label" style="margin-top:1.5rem">Players</div>
              <ul class="tv-player-list">
                @for (p of rt.players$(); track p.playerId) {
                  <li class="tv-player">
                    <span class="tv-player-name">{{ p.displayName }}</span>
                    @if (p.isHost) { <span class="tv-host-badge">host</span> }
                  </li>
                }
              </ul>
            }
          </div>

          <!-- CENTER PANEL: performance state -->
          <div class="tv-center">
            @if (rt.currentPerformance$()) {
              @let perf = rt.currentPerformance$()!;

              @if (perf.state === 'CONFIRMING') {
                <div class="tv-perf-banner confirming">
                  <div class="tv-perf-title">Confirming performers…</div>
                  <div class="tv-countdown">{{ secondsLeft() }}s</div>
                  <div style="font-size:1.25rem;color:#94a3b8;margin-top:.75rem">
                    Perform to confirm on your phone
                  </div>
                </div>
              }

              @if (perf.state === 'RUNNING') {
                <div class="tv-perf-banner running">
                  <div class="tv-perf-title">Performance in progress</div>
                  @if (youtubeEmbedUrl()) {
                    <iframe [src]="youtubeEmbedUrl()!" width="640" height="360"
                      style="margin-top:1.25rem;border-radius:8px;border:none"
                      allow="autoplay; encrypted-media" allowfullscreen></iframe>
                  }
                  <div style="font-size:1.1rem;color:#86efac;margin-top:1rem">
                    Judges — rate on your phone!
                  </div>
                </div>
              }

              @if (perf.state === 'LOCKED') {
                <div class="tv-perf-banner locked">
                  <div class="tv-perf-title" style="margin-bottom:1.5rem">Results</div>
                  <div style="display:flex;flex-direction:column;gap:1rem;width:100%;max-width:500px">
                    @for (s of rt.lockedScores$(); track s.playerId) {
                      <div class="tv-score-row">
                        <span class="tv-score-name">{{ s.displayName }}</span>
                        <span class="tv-score-pts">{{ s.points }} pts</span>
                      </div>
                    }
                  </div>
                </div>
              }

              @if (perf.state === 'SKIPPED') {
                <div class="tv-perf-banner" style="background:#1e293b">
                  <div class="tv-perf-title" style="color:#94a3b8">Performance skipped</div>
                </div>
              }
            } @else if (rt.gameState$() === 'ACTIVE') {
              <div class="tv-center-idle">
                <div class="tv-title">KaraoGUI</div>
                <div class="tv-subtitle">Waiting for next performance…</div>
              </div>
            } @else {
              <div class="tv-center-idle">
                <div class="tv-title">KaraoGUI</div>
                <div class="tv-subtitle">Scan the join code to play</div>
              </div>
            }
          </div>

          <!-- RIGHT PANEL: live ranking + comments -->
          <div class="tv-right">
            <div class="tv-label">Ranking</div>
            @if (rt.ranking$().length) {
              <ol class="tv-rank-list">
                @for (entry of rt.ranking$(); track entry.playerId) {
                  <li class="tv-rank-entry">
                    <span class="tv-rank-pos">{{ entry.rank }}</span>
                    <span class="tv-rank-name">{{ entry.displayName }}</span>
                    <span class="tv-rank-score">{{ entry.score }} pts</span>
                  </li>
                }
              </ol>
            } @else {
              <div style="color:#4b5563;font-size:1rem">No scores yet</div>
            }

            @if (rt.comments$().length) {
              <div class="tv-label" style="margin-top:1.5rem">Chat</div>
              <div class="tv-comments">
                @for (c of rt.comments$().slice(0, 8); track c.commentId) {
                  <div class="tv-comment">
                    <span class="tv-comment-author">{{ c.authorName }}</span>
                    <span class="tv-comment-body">{{ c.body }}</span>
                    @if (c.likeCount > 0) {
                      <span class="tv-comment-likes">❤ {{ c.likeCount }}</span>
                    }
                  </div>
                }
              </div>
            }
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
      display: grid;
      grid-template-columns: 320px 1fr 260px;
      gap: 3rem;
      align-items: flex-start;
      width: 100%;
      max-width: 1400px;
    }
    .tv-left { flex-shrink: 0; }
    .tv-center {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 60vh;
    }
    .tv-center-idle { text-align: center; }
    .tv-right { flex-shrink: 0; }

    .tv-label {
      font-size: .875rem;
      text-transform: uppercase;
      letter-spacing: .15em;
      color: #6b7280;
      margin-bottom: .5rem;
    }
    .tv-join-code {
      font-size: 3rem;
      font-weight: 900;
      letter-spacing: .2em;
      color: #a5b4fc;
      line-height: 1;
      margin-bottom: 1.5rem;
      white-space: nowrap;
    }
    .tv-state-badge {
      display: inline-block;
      padding: .5rem 1.25rem;
      border-radius: 2rem;
      background: #1e293b;
      color: #94a3b8;
      font-size: .9rem;
      margin-bottom: 1.5rem;
    }
    .tv-state-badge.active { background: #14532d; color: #86efac; }

    .tv-slot-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: .6rem 1rem;
      border-radius: 8px;
      background: #1e293b;
    }
    .tv-slot-card.confirmed { background: #14532d; }
    .tv-slot-card.pending { background: #422006; }
    .tv-slot-name { font-size: 1rem; font-weight: 600; }
    .tv-slot-badge { font-size: .75rem; color: #94a3b8; }

    .tv-player-list { list-style: none; margin: 0; padding: 0; }
    .tv-player {
      display: flex;
      align-items: center;
      gap: .75rem;
      padding: .6rem 0;
      border-bottom: 1px solid #1e293b;
      font-size: 1.1rem;
    }
    .tv-player-name { flex: 1; font-weight: 500; }
    .tv-host-badge { font-size: .75rem; background: #6366f1; color: #fff; border-radius: 4px; padding: .1rem .4rem; }

    .tv-perf-banner {
      width: 100%;
      border-radius: 16px;
      padding: 3rem 2rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .tv-perf-banner.confirming { background: #1e1b4b; }
    .tv-perf-banner.running { background: #052e16; }
    .tv-perf-banner.locked { background: #1e293b; }

    .tv-perf-title {
      font-size: 2.5rem;
      font-weight: 800;
      color: #e2e8f0;
    }
    .tv-countdown {
      font-size: 6rem;
      font-weight: 900;
      color: #f59e0b;
      line-height: 1;
      margin-top: .5rem;
    }
    .tv-score-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: #0f172a;
      border-radius: 8px;
      padding: .75rem 1.25rem;
    }
    .tv-score-name { font-size: 1.5rem; font-weight: 600; color: #e2e8f0; }
    .tv-score-pts { font-size: 2rem; font-weight: 800; color: #a5b4fc; }

    .tv-rank-list { list-style: none; margin: 0; padding: 0; }
    .tv-rank-entry {
      display: flex;
      align-items: center;
      gap: .75rem;
      padding: .75rem 0;
      border-bottom: 1px solid #1e293b;
      font-size: 1.1rem;
    }
    .tv-rank-pos {
      width: 2rem;
      text-align: center;
      font-weight: 700;
      color: #6366f1;
      font-size: 1.25rem;
    }
    .tv-rank-name { flex: 1; font-weight: 500; }
    .tv-rank-score { color: #6b7280; font-size: .9rem; }

    .tv-comments { display: flex; flex-direction: column; gap: .5rem; }
    .tv-comment {
      background: #1e293b;
      border-radius: 8px;
      padding: .5rem .75rem;
      font-size: .85rem;
      display: flex;
      flex-direction: column;
      gap: .15rem;
    }
    .tv-comment-author { font-weight: 700; color: #a5b4fc; font-size: .75rem; }
    .tv-comment-body { color: #e2e8f0; line-height: 1.4; }
    .tv-comment-likes { color: #f87171; font-size: .75rem; }
  `],
})
export class TvLobbyComponent implements OnInit, OnDestroy {
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

  readonly youtubeEmbedUrl = computed((): SafeResourceUrl | null => {
    const url = this.rt.currentPerformance$()?.youtubeUrl;
    if (!url) return null;
    const id = this._extractYoutubeId(url);
    if (!id) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${id}?autoplay=1`);
  });

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

  private resnapSub: any = null;
  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private resultPageHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    public rt: RealtimeService,
    private route: ActivatedRoute,
    private api: GameApiService,
    private sanitizer: DomSanitizer,
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

  private _extractYoutubeId(url: string): string | null {
    const patterns = [
      /youtu\.be\/([^?&]+)/,
      /youtube\.com\/watch\?v=([^&]+)/,
      /youtube\.com\/embed\/([^?&]+)/,
    ];
    for (const p of patterns) {
      const m = url.match(p);
      if (m) return m[1];
    }
    return null;
  }

  tvMedal(rank: number): string {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
  }
}
