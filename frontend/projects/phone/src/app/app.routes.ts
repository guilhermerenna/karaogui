import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home').then(m => m.HomeComponent),
  },
  {
    path: 'create',
    loadComponent: () => import('./create/create-game').then(m => m.CreateGameComponent),
  },
  {
    path: 'join',
    loadComponent: () => import('./join/join-game').then(m => m.JoinGameComponent),
  },
  {
    path: 'lobby',
    loadComponent: () => import('./lobby/lobby').then(m => m.LobbyComponent),
  },
];
