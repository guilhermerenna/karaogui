import { Component, signal } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { usePlaygroundMode } from 'ui';
import { Playground } from './playground';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Playground],
  template: `
    @if (playground()) {
      @defer (on immediate) {
        <app-playground />
      }
    } @else {
      <router-outlet />
      @if (displayName()) {
        <div class="player-badge">{{ displayName() }}</div>
      }
    }
  `,
  styles: [`
    .player-badge {
      position: fixed;
      top: .75rem;
      right: .75rem;
      background: rgba(99,102,241,.15);
      color: #6366f1;
      border: 1px solid rgba(99,102,241,.3);
      border-radius: 2rem;
      padding: .25rem .75rem;
      font-size: .8rem;
      font-weight: 600;
      pointer-events: none;
      z-index: 1000;
      max-width: 160px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `],
})
export class App {
  protected readonly playground = usePlaygroundMode();
  displayName = signal<string>('');

  constructor(router: Router) {
    router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      const raw = sessionStorage.getItem('session');
      this.displayName.set(raw ? (JSON.parse(raw).displayName ?? '') : '');
    });
  }
}
