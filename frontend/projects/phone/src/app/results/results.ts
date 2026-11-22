import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  signal,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { RealtimeService } from 'realtime';

@Component({
  selector: 'app-results',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './results.html',
})
export class ResultsComponent implements OnInit, OnDestroy {
  private _pageIndex = signal(0);
  private _intervalHandle: ReturnType<typeof setInterval> | null = null;

  readonly pageIndex = this._pageIndex.asReadonly();

  readonly pageIndicators = computed(() => {
    const total = this.rt.ranking$().length;
    return Array.from({ length: Math.ceil(total / 5) });
  });

  readonly currentPage = computed(() => {
    const all = this.rt.ranking$();
    const start = this._pageIndex() * 5;
    return all.slice(start, start + 5);
  });

  constructor(public rt: RealtimeService, private router: Router) {}

  ngOnInit() {
    const raw = sessionStorage.getItem('session');
    if (!raw) {
      this.router.navigate(['/']);
      return;
    }

    this._intervalHandle = setInterval(() => {
      const total = this.rt.ranking$().length;
      const pages = Math.ceil(total / 5);
      if (pages > 1) {
        this._pageIndex.update(i => (i + 1) % pages);
      }
    }, 5000);
  }

  ngOnDestroy() {
    if (this._intervalHandle) clearInterval(this._intervalHandle);
  }

  medal(rank: number): string {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
  }
}
