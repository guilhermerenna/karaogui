import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { GameApiService } from 'api';

@Component({
  selector: 'ui-local-video-player',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="video-wrapper">
      <video
        #videoEl
        class="video-element"
        [src]="streamUrl()"
        autoplay
        playsinline
        controls
        (play)="autoplayBlocked.set(false)"
        (error)="onVideoError()">
      </video>

      @if (autoplayBlocked()) {
        <button type="button" class="autoplay-overlay" (click)="resumePlayback()">
          <span class="autoplay-icon">▶</span>
          <span>Click to start playback</span>
        </button>
      }
    </div>
  `,
  styles: [`
    .video-wrapper {
      position: relative;
      width: 100%;
      aspect-ratio: 16 / 9;
      border-radius: 8px;
      overflow: hidden;
      background: #000;
    }
    .video-element {
      width: 100%;
      height: 100%;
      display: block;
      object-fit: contain;
      background: #000;
    }
    .autoplay-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      background: rgba(15, 23, 42, 0.75);
      color: #f8fafc;
      border: none;
      cursor: pointer;
      font-size: 1.25rem;
      font-weight: 700;
    }
    .autoplay-icon {
      font-size: 3rem;
      line-height: 1;
    }
  `],
})
export class LocalVideoPlayerComponent implements OnDestroy {
  private readonly api = inject(GameApiService);
  private readonly videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoEl');

  readonly videoId = input.required<string>();
  readonly playbackError = output<void>();

  readonly autoplayBlocked = signal(false);
  readonly streamUrl = computed(() => this.api.getVideoStreamUrl(this.videoId()));

  private destroying = false;

  constructor() {
    effect(() => {
      const url = this.streamUrl();
      const el = this.videoRef()?.nativeElement;
      if (!el || !url) return;

      this.autoplayBlocked.set(false);
      el.load();
      void el.play().catch((err: unknown) => {
        if (err instanceof DOMException && err.name === 'NotAllowedError') {
          this.autoplayBlocked.set(true);
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.destroying = true;
    const el = this.videoRef()?.nativeElement;
    if (el) {
      el.pause();
      el.removeAttribute('src');
      el.load();
    }
  }

  resumePlayback(): void {
    const el = this.videoRef()?.nativeElement;
    if (!el) return;
    void el.play().then(() => this.autoplayBlocked.set(false)).catch(() => {});
  }

  onVideoError(): void {
    if (this.destroying) return;
    const el = this.videoRef()?.nativeElement;
    if (!el?.getAttribute('src')) return;
    this.playbackError.emit();
  }
}
