import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  effect,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { GameApiService } from 'api';
import { RealtimeService } from 'realtime';
import type { CommentDto, CriterionScore, SessionInfo } from 'contracts';

const CRITERIA = ['PITCH', 'ENERGY', 'STAGE_PRESENCE'] as const;

@Component({
  selector: 'app-performance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="page">
      <!-- FLOATING BACK BUTTON -->
      <button class="btn-back" (click)="router.navigate(['/'])">← Back</button>

      <div class="card" style="max-width:520px">

        @if (!session) {
          <p class="error-msg">No session found. <a href="/">Go back</a></p>
        } @else {
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem">
            <h2 style="margin:0">Performance</h2>
            <span style="font-size:.8rem;color:#888">{{ session.displayName }}</span>
          </div>

          <!-- COMMENTS (visible when game is ACTIVE, hidden for judges during a performance) -->
          @if (rt.gameState$() === 'ACTIVE' && !isJudge()) {
            <details style="margin-bottom:1.5rem" open>
              <summary style="cursor:pointer;font-weight:600">Comments ({{ rt.comments$().length }})</summary>
              <div style="margin-top:.75rem">

                <!-- Post form -->
                <div style="display:flex;flex-direction:column;gap:.5rem;margin-bottom:1rem">
                  <textarea [(ngModel)]="commentBody" maxlength="280" rows="3"
                    placeholder="Say something…"
                    style="width:100%;resize:none;border:1px solid #d1d5db;border-radius:6px;padding:.5rem;font-size:.875rem;box-sizing:border-box"></textarea>
                  <button class="btn btn-primary" style="align-self:flex-end"
                    [disabled]="postingComment() || !commentBody.trim()" (click)="postComment()">
                    Send
                  </button>
                </div>
                @if (commentError()) { <p class="error-msg" style="margin-bottom:.75rem">{{ commentError() }}</p> }

                <!-- Feed -->
                <div style="max-height:300px;overflow-y:auto">
                  @for (c of rt.comments$(); track c.commentId) {
                    <div style="border-bottom:1px solid #f0f0f0;padding:.6rem 0">
                      <div style="display:flex;justify-content:space-between;align-items:flex-start">
                        <span style="font-weight:600;font-size:.8rem">{{ c.authorName }}</span>
                        <button style="background:none;border:none;cursor:pointer;font-size:.8rem;color:#888;padding:0"
                          (click)="likeComment(c)">
                          ❤ {{ c.likeCount }}
                        </button>
                      </div>
                      <p style="margin:.25rem 0 0;font-size:.875rem">{{ c.body }}</p>
                    </div>
                  }
                </div>

                @if (canLoadMore()) {
                  <button class="btn" style="width:100%;margin-top:.75rem;font-size:.8rem" (click)="loadMoreComments()">
                    Load more
                  </button>
                }
              </div>
            </details>
          }

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
                <button class="btn btn-primary" [disabled]="confirming()" (click)="confirmSlot()">
                  {{ confirming() ? 'Confirming…' : 'Confirm' }}
                </button>
                @if (actionError()) { <p class="error-msg" style="margin-top:.5rem">{{ actionError() }}</p> }
              </div>
            }

            <!-- PERFORMER — CONFIRMED or RUNNING -->
            @if (mySlot() && (mySlot()!.state === 'CONFIRMED' || mySlot()!.state === 'REPLACED') && perf.state === 'RUNNING') {
              <div style="background:#dcfce7;border-radius:8px;padding:1.25rem;margin-bottom:1rem;text-align:center">
                <div style="font-size:1.5rem;">You're performing!</div>
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
            @if (isJudge() && evalToastVisible()) {
              <div style="position:fixed;top:1.5rem;left:50%;transform:translateX(-50%);z-index:100;background:#166534;color:#fff;border-radius:12px;padding:1rem 1.5rem;min-width:240px;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,.3)">
                <div style="font-weight:600;margin-bottom:.5rem">Evaluation submitted!</div>
                <button style="background:rgba(255,255,255,.2);border:none;color:#fff;border-radius:6px;padding:.3rem .9rem;cursor:pointer;font-size:.85rem"
                  (click)="dismissEval()">Dismiss</button>
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
                  @for (s of rt.lockedScores$(); track s.playerId) {
                    <li style="display:flex;justify-content:space-between;align-items:center;padding:.6rem 0;border-bottom:1px solid #f0f0f0">
                      <span>{{ s.displayName }}</span>
                      <span style="font-weight:600;color:#6366f1">{{ s.points }} pts</span>
                    </li>
                  }
                </ul>
              </div>

              <!-- END-GAME PROMPT — only when queue is empty and game not over -->
              @if (!rt.queueNonEmpty$() && !rt.gameEnded$()) {
                <div style="background:#f5f3ff;border-radius:8px;padding:1.25rem;margin-bottom:1rem;text-align:center">
                  <div style="font-weight:600;margin-bottom:.75rem">What's next?</div>
                  <p style="font-size:.85rem;color:#555;margin:0 0 1rem">Queue another performance below, or end the game.</p>
                  <button class="btn" style="background:#ef4444;color:#fff;border:none"
                    [disabled]="endingGame()" (click)="endGame()">
                    {{ endingGame() ? 'Ending…' : 'End Game' }}
                  </button>
                  @if (endGameError()) { <p class="error-msg" style="margin-top:.5rem">{{ endGameError() }}</p> }
                </div>
              }
            }

            <!-- SLOT STATUS -->
            <div style="margin-top:1rem;margin-bottom:1.5rem">
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

          <!-- QUEUE FORM (visible to all players) -->
          <details [attr.open]="rt.currentPerformance$() === null ? '' : null">
            <summary style="cursor:pointer;font-weight:600;margin-bottom:.75rem">Queue next performance</summary>
            <div style="margin-top:.75rem">
              <label style="display:block;font-size:.85rem;margin-bottom:.25rem">YouTube URL</label>
              <input type="url" [(ngModel)]="queueUrl" placeholder="https://youtu.be/..." class="input" style="margin-bottom:.75rem" />

              <label style="display:block;font-size:.85rem;margin-bottom:.25rem">Select performers</label>
              <div style="margin-bottom:.5rem">
                <label style="display:flex;align-items:center;gap:.75rem;font-size:.85rem">
                  <span style="white-space:nowrap">Performers: <strong>{{ slotCount() }}</strong></span>
                  <input type="range" min="1" [max]="maxPerformers()"
                    [value]="slotCount()" (input)="setSlotCount(+$any($event.target).value)"
                    style="flex:1" />
                  <span style="color:#9ca3af;font-size:.75rem">max {{ maxPerformers() }}</span>
                </label>
              </div>
              <div style="margin-bottom:.75rem">
                @for (p of rt.players$(); track p.playerId) {
                  <label style="display:flex;align-items:center;gap:.5rem;padding:.3rem 0"
                    [style.opacity]="!selectedPerformers().has(p.playerId) && selectedPerformers().size >= slotCount() ? '0.4' : '1'">
                    <input type="checkbox" [checked]="selectedPerformers().has(p.playerId)"
                      [disabled]="!selectedPerformers().has(p.playerId) && selectedPerformers().size >= slotCount()"
                      (change)="togglePerformer(p.playerId)" />
                    {{ p.displayName }}{{ p.playerId === session.playerId ? ' (you)' : '' }}
                  </label>
                }
              </div>

              <button class="btn btn-primary" [disabled]="queuing() || !queueUrl" (click)="showPreview()">
                Preview &amp; Queue
              </button>
              @if (queueError()) { <p class="error-msg" style="margin-top:.5rem">{{ queueError() }}</p> }
            </div>
          </details>

          <!-- YOUTUBE PREVIEW -->
          @if (previewEmbedUrl()) {
            <div style="position:fixed;inset:0;background:rgba(0,0,0,.75);z-index:200;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:1.5rem">
              <div style="background:#fff;border-radius:12px;padding:1.25rem;width:100%;max-width:480px">
                <div style="font-weight:600;margin-bottom:.75rem">Preview</div>
                <div style="position:relative;padding-bottom:56.25%;height:0;overflow:hidden;border-radius:8px;margin-bottom:1rem">
                  <iframe [src]="previewEmbedUrl()!" style="position:absolute;inset:0;width:100%;height:100%;border:none"
                    allow="autoplay; encrypted-media" allowfullscreen></iframe>
                </div>
                <p style="font-size:.8rem;color:#6b7280;margin:0 0 1rem">Does the video load correctly? If it has loaded and has started playing, confirm to queue it.</p>
                <div style="display:flex;gap:.75rem">
                  <button class="btn btn-primary" style="flex:1" [disabled]="queuing()" (click)="confirmQueue()">
                    {{ queuing() ? 'Queuing…' : 'Confirm' }}
                  </button>
                  <button class="btn" style="flex:1" (click)="cancelPreview()">Cancel</button>
                </div>
                @if (queueError()) { <p class="error-msg" style="margin-top:.5rem">{{ queueError() }}</p> }
              </div>
            </div>
          }

        }
      </div>
    </div>
  `,
  styles: [`
    .btn-back {
      position: fixed;
      top: 1.25rem;
      left: 1.25rem;
      background: rgba(99,102,241,.12);
      color: #6366f1;
      border: 1px solid rgba(99,102,241,.3);
      border-radius: 2rem;
      padding: .5rem 1.1rem;
      font-size: .85rem;
      font-weight: 600;
      cursor: pointer;
      z-index: 1000;
      backdrop-filter: blur(4px);
    }
    .btn-back:hover { background: rgba(99,102,241,.22); }
  `],
})
export class PerformanceComponent implements OnInit, OnDestroy {
  session: SessionInfo | null = null;
  readonly criteria = CRITERIA;

  queueUrl = '';
  selectedPerformers = signal<Set<string>>(new Set());
  slotCount = signal(1);
  // Reserve at least 1 judge and 1 audience member: performers <= players - 2.
  readonly maxPerformers = computed(() => Math.max(1, this.rt.players$().length - 2));
  queuing = signal(false);
  queueError = signal('');
  previewEmbedUrl = signal<SafeResourceUrl | null>(null);

  confirming = signal(false);
  volunteering = signal(false);
  submittingEval = signal(false);
  submittingRating = signal(false);
  // Track WHICH performance the eval/rating was submitted for, so the state
  // resets automatically when a new performance is announced.
  private evalSubmittedFor = signal<number | null>(null);
  private ratingSubmittedFor = signal<number | null>(null);
  // Toast visibility is independent of submitted state: dismissing the toast
  // must NOT reopen the eval form while the performance is still RUNNING.
  evalToastVisible = signal(false);
  actionError = signal('');

  readonly evalSubmitted = computed(() => {
    const perf = this.rt.currentPerformance$();
    return !!perf && this.evalSubmittedFor() === perf.performanceId;
  });

  readonly ratingSubmitted = computed(() => {
    const perf = this.rt.currentPerformance$();
    return !!perf && this.ratingSubmittedFor() === perf.performanceId;
  });

  secondsLeft = signal(0);
  overallScore = signal(5);

  evalScores: Record<string, number> = { PITCH: 5, ENERGY: 5, STAGE_PRESENCE: 5 };

  endingGame = signal(false);
  endGameError = signal('');

  commentBody = '';
  postingComment = signal(false);
  commentError = signal('');
  private _commentPage = 0;
  private _totalCommentPages = 1;

  readonly canLoadMore = computed(() => this._commentPage < this._totalCommentPages - 1);

  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private evalDismissHandle: ReturnType<typeof setTimeout> | null = null;
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

  constructor(public rt: RealtimeService, private api: GameApiService, public router: Router, private sanitizer: DomSanitizer) {
    effect(() => {
      if (this.rt.gameEnded$()) {
        this.router.navigate(['/results']);
      }
    });

    // When a new performance is announced, hide any lingering toast and reset
    // the score sliders so this judge/audience member starts fresh.
    let lastPerfId: number | null = null;
    effect(() => {
      const perf = this.rt.currentPerformance$();
      const id = perf?.performanceId ?? null;
      if (id !== lastPerfId) {
        lastPerfId = id;
        this.dismissEval();
        this.evalScores = { PITCH: 5, ENERGY: 5, STAGE_PRESENCE: 5 };
        this.overallScore.set(5);
      }
    });
  }

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
        if (snap.state !== 'ACTIVE' && snap.state !== 'OVER') {
          this.router.navigate(['/lobby']);
          return;
        }
        if (!this.rt.client) {
          this.rt.connect(this.session!.gameId, this.session!.token, 'PHONE');
        }
        this._loadComments(0);
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
    if (this.evalDismissHandle) clearTimeout(this.evalDismissHandle);
  }

  setSlotCount(n: number) {
    const clamped = Math.max(1, Math.min(n, this.maxPerformers()));
    this.slotCount.set(clamped);
    // drop checked players beyond the new cap
    if (this.selectedPerformers().size > clamped) {
      const kept = [...this.selectedPerformers()].slice(0, clamped);
      this.selectedPerformers.set(new Set(kept));
    }
  }

  togglePerformer(id: string) {
    this.selectedPerformers.update(s => {
      const next = new Set(s);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  showPreview() {
    if (!this.queueUrl) return;
    const id = this._extractYoutubeId(this.queueUrl);
    if (!id) {
      this.queueError.set('Could not parse YouTube URL. Please check the link.');
      return;
    }
    this.queueError.set('');
    this.previewEmbedUrl.set(
      this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${id}?autoplay=1`)
    );
  }

  cancelPreview() {
    this.previewEmbedUrl.set(null);
    this.queueError.set('');
  }

  confirmQueue() {
    if (!this.session || !this.queueUrl) return;
    this.queuing.set(true);
    this.queueError.set('');
    this.api.queuePerformance(this.session.gameId, this.session.token, {
      type: 'KARAOKE',
      youtubeUrl: this.queueUrl,
      performerPlayerIds: [...this.selectedPerformers()],
      slotCount: this.slotCount(),
    }).subscribe({
      next: () => {
        this.queuing.set(false);
        this.previewEmbedUrl.set(null);
        this.queueUrl = '';
        this.selectedPerformers.set(new Set());
        this.slotCount.set(1);
      },
      error: (err: HttpErrorResponse) => {
        this.queuing.set(false);
        this.queueError.set(err.error?.error?.message ?? 'Could not queue performance.');
      },
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
        this.actionError.set(err.error?.error?.message ?? 'Could not confirm slot.');
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
        this.actionError.set(err.error?.error?.message ?? 'Could not volunteer.');
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
        this.evalSubmittedFor.set(perf.performanceId);
        this.evalToastVisible.set(true);
        this.evalDismissHandle = setTimeout(() => this.dismissEval(), 5000);
      },
      error: (err: HttpErrorResponse) => {
        this.submittingEval.set(false);
        this.actionError.set(err.error?.error?.message ?? 'Could not submit evaluation.');
      },
    });
  }

  dismissEval() {
    if (this.evalDismissHandle) { clearTimeout(this.evalDismissHandle); this.evalDismissHandle = null; }
    this.evalToastVisible.set(false);
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
        this.ratingSubmittedFor.set(perf.performanceId);
      },
      error: (err: HttpErrorResponse) => {
        this.submittingRating.set(false);
        this.actionError.set(err.error?.error?.message ?? 'Could not submit rating.');
      },
    });
  }

  endGame() {
    if (!this.session) return;
    this.endingGame.set(true);
    this.endGameError.set('');
    this.api.endGame(this.session.gameId, this.session.token).subscribe({
      next: () => this.endingGame.set(false),
      error: (err: HttpErrorResponse) => {
        this.endingGame.set(false);
        this.endGameError.set(err.error?.error?.message ?? 'Could not end game.');
      },
    });
  }

  postComment() {
    if (!this.session || !this.commentBody.trim()) return;
    this.postingComment.set(true);
    this.commentError.set('');
    const body = this.commentBody.trim();
    this.api.postComment(this.session.gameId, this.session.token, body).subscribe({
      next: () => {
        this.postingComment.set(false);
        this.commentBody = '';
      },
      error: (err: HttpErrorResponse) => {
        this.postingComment.set(false);
        this.commentError.set(err.error?.error?.message ?? 'Could not post comment.');
      },
    });
  }

  likeComment(comment: CommentDto) {
    if (!this.session) return;
    this.api.likeComment(this.session.gameId, this.session.token, comment.commentId).subscribe();
  }

  loadMoreComments() {
    if (!this.session || !this.canLoadMore()) return;
    this._loadComments(this._commentPage + 1);
  }

  private _loadComments(page: number) {
    if (!this.session) return;
    this.api.getComments(this.session.gameId, this.session.token, page).subscribe({
      next: (comments) => {
        this._commentPage = page;
        if (page === 0) {
          this.rt.prependComments(comments);
        } else {
          this.rt.appendComments(comments);
        }
        this._totalCommentPages = comments.length === 20 ? page + 2 : page + 1;
      },
    });
  }
}
