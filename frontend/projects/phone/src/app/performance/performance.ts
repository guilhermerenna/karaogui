import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { GameApiService } from 'api';
import { RealtimeService } from 'realtime';
import type { CriterionScore, SessionInfo } from 'contracts';

const CRITERIA = ['PITCH', 'ENERGY', 'STAGE_PRESENCE'] as const;

@Component({
  selector: 'app-performance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="card" style="max-width:520px">

        @if (!session) {
          <p class="error-msg">No session found. <a href="/">Go back</a></p>
        } @else {
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem">
            <h2 style="margin:0">Performance</h2>
            <span style="font-size:.8rem;color:#888">{{ session.displayName }}</span>
          </div>

          <!-- QUEUE FORM (visible to all players) -->
          <details style="margin-bottom:1.5rem" [attr.open]="rt.currentPerformance$() === null ? '' : null">
              <summary style="cursor:pointer;font-weight:600;margin-bottom:.75rem">Queue next performance</summary>
              <div style="margin-top:.75rem">
                <label style="display:block;font-size:.85rem;margin-bottom:.25rem">YouTube URL</label>
                <input type="url" [(ngModel)]="queueUrl" placeholder="https://youtu.be/..." class="input" style="margin-bottom:.75rem" />

                <label style="display:block;font-size:.85rem;margin-bottom:.25rem">Select performers</label>
                <div style="margin-bottom:.75rem">
                  @for (p of rt.players$(); track p.playerId) {
                    @if (p.playerId !== session.playerId) {
                      <label style="display:flex;align-items:center;gap:.5rem;padding:.3rem 0">
                        <input type="checkbox" [checked]="selectedPerformers().has(p.playerId)"
                          (change)="togglePerformer(p.playerId)" />
                        {{ p.displayName }}
                      </label>
                    }
                  }
                </div>

                <button class="btn btn-primary" [disabled]="queuing() || !queueUrl" (click)="queuePerformance()">
                  {{ queuing() ? 'Queuing…' : 'Queue Karaoke' }}
                </button>
                @if (queueError()) { <p class="error-msg" style="margin-top:.5rem">{{ queueError() }}</p> }
              </div>
            </details>

          <!-- NO ACTIVE PERFORMANCE -->
          @if (rt.currentPerformance$() === null) {
            <div style="text-align:center;color:#888;padding:2rem 0">
              Waiting for next performance…
            </div>
          } @else {
            @let perf = rt.currentPerformance$()!;

            <!-- PERFORMER — PENDING (must confirm) -->
            @if (mySlot() && mySlot()!.state === 'PENDING' && perf.state === 'CONFIRMING') {
              <div style="background:#eff6ff;border-radius:8px;padding:1.25rem;margin-bottom:1rem">
                <div style="font-weight:600;margin-bottom:.5rem">You're up! Confirm your slot</div>
                <div style="font-size:.85rem;color:#555;margin-bottom:.75rem">
                  Time left: {{ secondsLeft() }}s
                </div>
                <div style="font-size:.85rem;color:#555;margin-bottom:.75rem">
                  Song: <a [href]="perf.youtubeUrl!" target="_blank">Open YouTube</a>
                </div>
                <button class="btn btn-primary" [disabled]="confirming()" (click)="confirmSlot()">
                  {{ confirming() ? 'Confirming…' : 'Confirm' }}
                </button>
                @if (actionError()) { <p class="error-msg" style="margin-top:.5rem">{{ actionError() }}</p> }
              </div>
            }

            <!-- PERFORMER — CONFIRMED or RUNNING -->
            @if (mySlot() && (mySlot()!.state === 'CONFIRMED' || mySlot()!.state === 'REPLACED') && perf.state === 'RUNNING') {
              <div style="background:#dcfce7;border-radius:8px;padding:1.25rem;margin-bottom:1rem;text-align:center">
                <div style="font-size:1.5rem;margin-bottom:.5rem">You're performing!</div>
                <a [href]="perf.youtubeUrl!" target="_blank" class="btn btn-primary" style="display:inline-block;width:auto">
                  Open Song
                </a>
              </div>
            }

            <!-- VOLUNTEER PANEL -->
            @if (!mySlot() && perf.state === 'CONFIRMING' && replacementOpen() && hasPendingSlots()) {
              <div style="background:#fefce8;border-radius:8px;padding:1.25rem;margin-bottom:1rem">
                <div style="font-weight:600;margin-bottom:.5rem">Open slot available!</div>
                <div style="font-size:.85rem;color:#555;margin-bottom:.75rem">A performer didn't confirm. Volunteer to fill their slot.</div>
                <button class="btn btn-primary" [disabled]="volunteering()" (click)="volunteerSlot()">
                  {{ volunteering() ? 'Volunteering…' : 'Volunteer' }}
                </button>
                @if (actionError()) { <p class="error-msg" style="margin-top:.5rem">{{ actionError() }}</p> }
              </div>
            }

            <!-- JUDGE EVAL FORM -->
            @if (isJudge() && perf.state === 'RUNNING' && !evalSubmitted()) {
              <div style="margin-bottom:1.5rem">
                <h3 style="margin:0 0 1rem">Rate this performance</h3>
                @for (c of criteria; track c) {
                  <div style="margin-bottom:1rem">
                    <label style="display:flex;justify-content:space-between;font-size:.9rem;margin-bottom:.25rem">
                      <span>{{ c.replace('_', ' ') }}</span>
                      <strong>{{ evalScores[c] }}</strong>
                    </label>
                    <input type="range" min="1" max="10" [(ngModel)]="evalScores[c]" style="width:100%" />
                  </div>
                }
                <button class="btn btn-primary" [disabled]="submittingEval()" (click)="submitEvaluation()">
                  {{ submittingEval() ? 'Submitting…' : 'Submit Evaluation' }}
                </button>
                @if (actionError()) { <p class="error-msg" style="margin-top:.5rem">{{ actionError() }}</p> }
              </div>
            }
            @if (isJudge() && evalSubmitted()) {
              <div style="background:#dcfce7;border-radius:8px;padding:1rem;margin-bottom:1rem;text-align:center;color:#166534">
                Evaluation submitted!
              </div>
            }

            <!-- AUDIENCE RATING -->
            @if (!isJudge() && !mySlot() && perf.state === 'RUNNING' && !ratingSubmitted()) {
              <div style="margin-bottom:1.5rem">
                <h3 style="margin:0 0 1rem">Rate the performance</h3>
                <label style="display:flex;justify-content:space-between;font-size:.9rem;margin-bottom:.25rem">
                  <span>Overall</span>
                  <strong>{{ overallScore() }}</strong>
                </label>
                <input type="range" min="1" max="10" [ngModel]="overallScore()" (ngModelChange)="overallScore.set($event)" style="width:100%;margin-bottom:1rem" />
                <button class="btn btn-primary" [disabled]="submittingRating()" (click)="submitRating()">
                  {{ submittingRating() ? 'Submitting…' : 'Submit Rating' }}
                </button>
                @if (actionError()) { <p class="error-msg" style="margin-top:.5rem">{{ actionError() }}</p> }
              </div>
            }
            @if (!isJudge() && !mySlot() && ratingSubmitted()) {
              <div style="background:#dcfce7;border-radius:8px;padding:1rem;margin-bottom:1rem;text-align:center;color:#166534">
                Rating submitted!
              </div>
            }

            <!-- SCOREBOARD (LOCKED) -->
            @if (perf.state === 'LOCKED') {
              <div style="margin-bottom:1.5rem">
                <h3 style="margin:0 0 1rem">Results</h3>
                <ul style="list-style:none;padding:0;margin:0">
                  @for (slot of perf.slots; track slot.slotId) {
                    <li style="display:flex;justify-content:space-between;align-items:center;padding:.6rem 0;border-bottom:1px solid #f0f0f0">
                      <span>{{ slot.currentPlayerName }}</span>
                      <span style="font-weight:600;color:#6366f1">{{ slot.state }}</span>
                    </li>
                  }
                </ul>
              </div>
            }

            <!-- SLOT STATUS -->
            <div style="margin-top:1rem">
              <div style="font-size:.8rem;color:#888;margin-bottom:.5rem">Performers</div>
              <div style="display:flex;flex-wrap:wrap;gap:.5rem">
                @for (slot of perf.slots; track slot.slotId) {
                  <div style="padding:.3rem .75rem;border-radius:20px;font-size:.8rem;font-weight:600"
                    [style.background]="slot.state === 'CONFIRMED' || slot.state === 'REPLACED' ? '#dcfce7' : '#fef9c3'"
                    [style.color]="slot.state === 'CONFIRMED' || slot.state === 'REPLACED' ? '#166534' : '#854d0e'">
                    {{ slot.currentPlayerName }}
                  </div>
                }
              </div>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class PerformanceComponent implements OnInit, OnDestroy {
  session: SessionInfo | null = null;
  readonly criteria = CRITERIA;

  queueUrl = '';
  selectedPerformers = signal<Set<string>>(new Set());
  queuing = signal(false);
  queueError = signal('');

  confirming = signal(false);
  volunteering = signal(false);
  submittingEval = signal(false);
  submittingRating = signal(false);
  evalSubmitted = signal(false);
  ratingSubmitted = signal(false);
  actionError = signal('');

  secondsLeft = signal(0);
  overallScore = signal(5);

  evalScores: Record<string, number> = { PITCH: 5, ENERGY: 5, STAGE_PRESENCE: 5 };

  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private resnapSub: any = null;

  readonly mySlot = computed(() => {
    if (!this.session) return null;
    const perf = this.rt.currentPerformance$();
    if (!perf?.slots?.length) return null;
    return perf.slots.find(s => s.currentPlayerId === this.session!.playerId) ?? null;
  });

  readonly isJudge = computed(() => {
    if (!this.session) return false;
    return this.rt.judgeIds$().includes(this.session.playerId);
  });

  readonly replacementOpen = computed(() => {
    const perf = this.rt.currentPerformance$();
    if (!perf?.replacementOpensAt) return false;
    return Date.now() >= new Date(perf.replacementOpensAt).getTime();
  });

  readonly hasPendingSlots = computed(() => {
    const perf = this.rt.currentPerformance$();
    if (!perf?.slots?.length) return false;
    return perf.slots.some(s => s.state === 'PENDING');
  });

  constructor(public rt: RealtimeService, private api: GameApiService, private router: Router) {}

  ngOnInit() {
    const raw = sessionStorage.getItem('session');
    if (!raw) {
      this.router.navigate(['/']);
      return;
    }
    this.session = JSON.parse(raw) as SessionInfo;

    this.api.getSnapshot(this.session.gameId, this.session.token).subscribe({
      next: (snap) => {
        this.rt.applySnapshot(snap);
        if (!this.rt.client) {
          this.rt.connect(this.session!.gameId, this.session!.token, 'PHONE');
        }
      },
      error: () => this.router.navigate(['/']),
    });

    this.resnapSub = this.rt.resnap$.subscribe(() => {
      if (!this.session) return;
      this.api.getSnapshot(this.session.gameId, this.session.token).subscribe({
        next: (snap) => this.rt.applySnapshot(snap),
      });
    });

    this.timerHandle = setInterval(() => {
      const perf = this.rt.currentPerformance$();
      if (perf?.confirmDeadlineAt) {
        const ms = new Date(perf.confirmDeadlineAt).getTime() - Date.now();
        this.secondsLeft.set(Math.max(0, Math.ceil(ms / 1000)));
      }
    }, 500);
  }

  ngOnDestroy() {
    this.resnapSub?.unsubscribe();
    if (this.timerHandle) clearInterval(this.timerHandle);
  }

  togglePerformer(id: string) {
    this.selectedPerformers.update(s => {
      const next = new Set(s);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  queuePerformance() {
    if (!this.session || !this.queueUrl) return;
    this.queuing.set(true);
    this.queueError.set('');
    this.api.queuePerformance(this.session.gameId, this.session.token, {
      type: 'KARAOKE',
      youtubeUrl: this.queueUrl,
      performerPlayerIds: [...this.selectedPerformers()],
    }).subscribe({
      next: () => {
        this.queuing.set(false);
        this.queueUrl = '';
        this.selectedPerformers.set(new Set());
      },
      error: (err: HttpErrorResponse) => {
        this.queuing.set(false);
        this.queueError.set(err.error?.message ?? 'Could not queue performance.');
      },
    });
  }

  confirmSlot() {
    if (!this.session) return;
    const perf = this.rt.currentPerformance$();
    if (!perf) return;
    this.confirming.set(true);
    this.actionError.set('');
    this.api.confirmSlot(this.session.gameId, perf.performanceId, this.session.token).subscribe({
      next: () => this.confirming.set(false),
      error: (err: HttpErrorResponse) => {
        this.confirming.set(false);
        this.actionError.set(err.error?.message ?? 'Could not confirm slot.');
      },
    });
  }

  volunteerSlot() {
    if (!this.session) return;
    const perf = this.rt.currentPerformance$();
    if (!perf) return;
    this.volunteering.set(true);
    this.actionError.set('');
    this.api.volunteerSlot(this.session.gameId, perf.performanceId, this.session.token).subscribe({
      next: () => this.volunteering.set(false),
      error: (err: HttpErrorResponse) => {
        this.volunteering.set(false);
        this.actionError.set(err.error?.message ?? 'Could not volunteer.');
      },
    });
  }

  submitEvaluation() {
    if (!this.session) return;
    const perf = this.rt.currentPerformance$();
    if (!perf) return;
    this.submittingEval.set(true);
    this.actionError.set('');
    const baseline: CriterionScore[] = CRITERIA.map(c => ({ criterion: c, score: Number(this.evalScores[c]) }));
    this.api.submitEvaluation(this.session.gameId, perf.performanceId, this.session.token, {
      baseline,
      perPerformer: {},
    }).subscribe({
      next: () => {
        this.submittingEval.set(false);
        this.evalSubmitted.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.submittingEval.set(false);
        this.actionError.set(err.error?.message ?? 'Could not submit evaluation.');
      },
    });
  }

  submitRating() {
    if (!this.session) return;
    const perf = this.rt.currentPerformance$();
    if (!perf) return;
    this.submittingRating.set(true);
    this.actionError.set('');
    this.api.submitRating(this.session.gameId, perf.performanceId, this.session.token, {
      overallScore: this.overallScore(),
    }).subscribe({
      next: () => {
        this.submittingRating.set(false);
        this.ratingSubmitted.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.submittingRating.set(false);
        this.actionError.set(err.error?.message ?? 'Could not submit rating.');
      },
    });
  }
}
