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
  templateUrl: './local-video-player.html',
  styleUrl: './local-video-player.scss',
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
