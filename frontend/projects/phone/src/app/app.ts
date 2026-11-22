import { Component, signal } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { usePlaygroundMode } from 'ui';
import { Playground } from './playground';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Playground],
  templateUrl: './app.html',
  styleUrl: './app.scss',
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
