import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./lobby/tv-lobby').then(m => m.TvLobbyComponent),
  },
  {
    path: 'playground',
    canMatch: [() => isDevMode()],
    loadComponent: () => import('./playground').then(m => m.Playground),
  },
];
