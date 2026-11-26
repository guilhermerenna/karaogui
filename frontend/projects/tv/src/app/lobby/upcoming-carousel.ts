import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  input,
  signal,
} from '@angular/core';
import type { QueuedPerformanceDto } from 'contracts';

const PAGE_SIZE = 2;
const CYCLE_INTERVAL_MS = 5000;

export interface UpcomingCard {
  item: QueuedPerformanceDto;
  position: number;
}

@Component({
  selector: 'app-upcoming-carousel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './upcoming-carousel.html',
  styleUrl: './upcoming-carousel.scss',
})
export class UpcomingCarouselComponent implements OnInit, OnDestroy {
  readonly items = input.required<QueuedPerformanceDto[]>();

  private readonly _pageIndex = signal(0);
  private intervalHandle: ReturnType<typeof setInterval> | null = null;

  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.items().length / PAGE_SIZE)));
  readonly activePage = computed(() => this._pageIndex() % this.pageCount());
  readonly pageDots = computed(() => Array.from({ length: this.pageCount() }));

  readonly visibleCards = computed<UpcomingCard[]>(() => {
    const start = this.activePage() * PAGE_SIZE;
    return this.items()
      .slice(start, start + PAGE_SIZE)
      .map((item, idx) => ({ item, position: start + idx + 1 }));
  });

  ngOnInit(): void {
    this.intervalHandle = setInterval(() => {
      if (this.pageCount() > 1) {
        this._pageIndex.update((i) => (i + 1) % this.pageCount());
      }
    }, CYCLE_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.intervalHandle) {
      clearInterval(this.intervalHandle);
      this.intervalHandle = null;
    }
  }

  songLabel(item: QueuedPerformanceDto): string {
    return item.songTitle?.trim() || item.videoName?.trim() || 'Karaoke';
  }

  singersLabel(item: QueuedPerformanceDto): string {
    return item.performerNames.length ? item.performerNames.join(', ') : 'TBD';
  }
}
