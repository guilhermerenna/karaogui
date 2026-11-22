import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'ui-youtube-video-player',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './youtube-video-player.html',
  styleUrl: './youtube-video-player.scss',
})
export class YoutubeVideoPlayerComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly youtubeUrl = input.required<string>();

  readonly embedUrl = computed((): SafeResourceUrl | null => {
    const id = this.extractYoutubeId(this.youtubeUrl());
    if (!id) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube.com/embed/${id}?autoplay=1`,
    );
  });

  private extractYoutubeId(url: string): string | null {
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
}
