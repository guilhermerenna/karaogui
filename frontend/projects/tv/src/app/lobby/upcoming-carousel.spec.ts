import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import type { QueuedPerformanceDto } from 'contracts';
import { UpcomingCarouselComponent } from './upcoming-carousel';

const MOCK_ITEMS: QueuedPerformanceDto[] = [
  {
    performanceId: 1,
    queuePosition: 1,
    songTitle: 'Bohemian Rhapsody',
    artist: 'Queen',
    videoName: 'Queen - Bohemian Rhapsody',
    performerNames: ['Ana', 'Bob'],
  },
  {
    performanceId: 2,
    queuePosition: 2,
    songTitle: null,
    artist: null,
    videoName: 'Radiohead - Creep',
    performerNames: ['Cara'],
  },
  {
    performanceId: 3,
    queuePosition: 3,
    songTitle: 'Mr. Brightside',
    artist: 'The Killers',
    videoName: 'Mr. Brightside',
    performerNames: ['Dan'],
  },
];

describe('UpcomingCarouselComponent', () => {
  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [UpcomingCarouselComponent],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders NEXT badge, song/artist or videoName fallback, and singers', () => {
    const fixture = TestBed.createComponent(UpcomingCarouselComponent);
    fixture.componentRef.setInput('items', MOCK_ITEMS.slice(0, 2));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const badges = Array.from(el.querySelectorAll('.upcoming-badge')).map((n) => n.textContent?.trim());
    expect(badges).toEqual(['NEXT', '#2']);

    const songs = Array.from(el.querySelectorAll('.upcoming-song')).map((n) => n.textContent?.trim());
    expect(songs).toEqual(['Bohemian Rhapsody', 'Radiohead - Creep']);

    const singers = Array.from(el.querySelectorAll('.upcoming-singer-names')).map((n) => n.textContent?.trim());
    expect(singers).toEqual(['Ana, Bob', 'Cara']);
    expect(el.querySelectorAll('.upcoming-dot').length).toBe(0);

    fixture.destroy();
  });

  it('cycles pages across 3+ items on a 5-second interval', () => {
    const fixture = TestBed.createComponent(UpcomingCarouselComponent);
    fixture.componentRef.setInput('items', MOCK_ITEMS);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.upcoming-dot').length).toBe(2);
    expect(el.textContent).toContain('Bohemian Rhapsody');
    expect(el.textContent).not.toContain('Mr. Brightside');

    vi.advanceTimersByTime(5000);
    fixture.detectChanges();

    expect(el.textContent).toContain('Mr. Brightside');
    expect(el.textContent).toContain('Dan');
    expect(el.textContent).not.toContain('Bohemian Rhapsody');

    fixture.destroy();
  });
});
