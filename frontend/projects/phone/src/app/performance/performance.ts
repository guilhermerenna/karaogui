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
import type { CommentDto, CriterionScore, SessionInfo, VideoDto } from 'contracts';

const CRITERIA = ['PITCH', 'ENERGY', 'STAGE_PRESENCE'] as const;

@Component({
  selector: 'app-performance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './performance.html',
  styleUrl: './performance.scss',
})
export class PerformanceComponent implements OnInit, OnDestroy {
  session: SessionInfo | null = null;
  readonly criteria = CRITERIA;

  selectedPerformers = signal<Set<string>>(new Set());
  slotCount = signal(1);
  // Reserve at least 1 judge and 1 audience member: performers <= players - 2.
  readonly maxPerformers = computed(() => Math.max(1, this.rt.players$().length - 2));
  queuing = signal(false);
  queueError = signal('');

  // Video library search
  searchQuery = signal('');
  videoResults = signal<VideoDto[]>([]);
  videoPage = signal(0);
  videoHasMore = signal(false);
  searching = signal(false);
  selectedVideo = signal<VideoDto | null>(null);
  deletingId = signal<string | null>(null);
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  // Import-to-library modal
  importOpen = signal(false);
  importUrl = '';
  importSongTitle = '';
  importArtist = '';
  importEmbedUrl = signal<SafeResourceUrl | null>(null);
  importing = signal(false);
  importError = signal('');

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
        this.runSearch();
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
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
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

  onSearchInput(value: string) {
    this.searchQuery.set(value);
    this.videoPage.set(0);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.runSearch(), 300);
  }

  runSearch() {
    if (!this.session) return;
    this.searching.set(true);
    this.api.searchVideos(this.session.token, this.searchQuery().trim(), this.videoPage()).subscribe({
      next: (res) => {
        this.searching.set(false);
        this.videoResults.set(res.content);
        this.videoHasMore.set(res.hasMore);
        // Keep a selection only if it's still visible in the results.
        const sel = this.selectedVideo();
        if (sel && !res.content.some(v => v.videoId === sel.videoId)) {
          this.selectedVideo.set(null);
        }
      },
      error: () => {
        this.searching.set(false);
        this.videoResults.set([]);
        this.videoHasMore.set(false);
      },
    });
  }

  nextVideoPage() {
    if (!this.videoHasMore()) return;
    this.videoPage.update(p => p + 1);
    this.runSearch();
  }

  prevVideoPage() {
    if (this.videoPage() === 0) return;
    this.videoPage.update(p => p - 1);
    this.runSearch();
  }

  selectVideo(v: VideoDto) {
    this.selectedVideo.set(this.selectedVideo()?.videoId === v.videoId ? null : v);
  }

  deleteVideo(v: VideoDto, event: Event) {
    event.stopPropagation();
    if (!this.session) return;
    this.deletingId.set(v.videoId);
    this.api.deleteVideo(this.session.token, v.videoId).subscribe({
      next: () => {
        this.deletingId.set(null);
        if (this.selectedVideo()?.videoId === v.videoId) this.selectedVideo.set(null);
        this.runSearch();
      },
      error: () => this.deletingId.set(null),
    });
  }

  openImport() {
    this.importOpen.set(true);
    this.importError.set('');
    this.importEmbedUrl.set(null);
    this.importUrl = '';
    this.importSongTitle = '';
    this.importArtist = '';
  }

  closeImport() {
    this.importOpen.set(false);
    this.importEmbedUrl.set(null);
    this.importError.set('');
  }

  previewImport() {
    if (!this.importUrl) return;
    const id = this._extractYoutubeId(this.importUrl);
    if (!id) {
      this.importError.set('Could not parse YouTube URL. Please check the link.');
      return;
    }
    this.importError.set('');
    this.importEmbedUrl.set(
      this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${id}?autoplay=1`)
    );
  }

  confirmImport() {
    if (!this.session || !this.importUrl) return;
    this.importing.set(true);
    this.importError.set('');
    this.api.importVideo(this.session.token, {
      youtubeUrl: this.importUrl,
      songTitle: this.importSongTitle.trim() || undefined,
      artist: this.importArtist.trim() || undefined,
    }).subscribe({
      next: (video) => {
        this.importing.set(false);
        this.closeImport();
        // Surface the newly-imported video immediately and pre-select it.
        this.searchQuery.set('');
        this.videoPage.set(0);
        this.selectedVideo.set(video);
        this.runSearch();
      },
      error: (err: HttpErrorResponse) => {
        this.importing.set(false);
        this.importError.set(err.error?.error?.message ?? 'Could not import video.');
      },
    });
  }

  confirmQueue() {
    if (!this.session || !this.selectedVideo()) return;
    this.queuing.set(true);
    this.queueError.set('');
    this.api.queuePerformance(this.session.gameId, this.session.token, {
      type: 'KARAOKE',
      videoId: this.selectedVideo()!.videoId,
      performerPlayerIds: [...this.selectedPerformers()],
      slotCount: this.slotCount(),
    }).subscribe({
      next: () => {
        this.queuing.set(false);
        this.selectedVideo.set(null);
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
