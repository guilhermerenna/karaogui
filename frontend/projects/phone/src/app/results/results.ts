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
  template: `
    <div class="page">
      <div class="card" style="max-width:520px">
        <h2 style="text-align:center;margin-bottom:.25rem">Game Over</h2>
        <p style="text-align:center;color:#888;margin-bottom:2rem">Final Standings</p>

        <!-- Cycling top-5 podium -->
        @if (currentPage().length) {
          <div style="margin-bottom:2rem">
            @for (entry of currentPage(); track entry.rank) {
              <div style="display:flex;align-items:center;gap:1rem;padding:.75rem;border-radius:8px;margin-bottom:.5rem"
                [style.background]="entry.rank === 1 ? '#fef9c3' : entry.rank === 2 ? '#f1f5f9' : entry.rank === 3 ? '#fef3c7' : '#f9fafb'">
                <span style="font-size:1.5rem;min-width:2rem;text-align:center">{{ medal(entry.rank) }}</span>
                <span style="flex:1;font-weight:600">{{ entry.displayName }}</span>
                <span style="font-weight:700;color:#6366f1">{{ entry.score }} pts</span>
              </div>
            }
          </div>

          <!-- Page indicator + progress -->
          <div style="display:flex;justify-content:center;gap:.5rem;margin-bottom:2rem">
            @for (p of pageIndicators(); track $index) {
              <div style="width:8px;height:8px;border-radius:50%"
                [style.background]="$index === pageIndex() ? '#6366f1' : '#d1d5db'"></div>
            }
          </div>
        }

        <!-- Full ranking table -->
        <h3 style="margin:0 0 1rem">Full Ranking</h3>
        <ul style="list-style:none;padding:0;margin:0 0 2rem">
          @for (entry of rt.ranking$(); track entry.rank) {
            <li style="display:flex;align-items:center;gap:.75rem;padding:.5rem 0;border-bottom:1px solid #f0f0f0">
              <span style="min-width:1.5rem;text-align:center;font-weight:600;color:#888">{{ entry.rank }}</span>
              <span style="flex:1">{{ entry.displayName }}</span>
              <span style="font-weight:600;color:#6366f1">{{ entry.score }}</span>
            </li>
          }
        </ul>

        <div style="text-align:center">
          <a href="/" class="btn btn-primary" style="display:inline-block;width:auto;text-decoration:none">
            Play Again
          </a>
        </div>
      </div>
    </div>
  `,
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
