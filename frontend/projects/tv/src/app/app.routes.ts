import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./lobby/tv-lobby').then(m => m.TvLobbyComponent),
  },
];
