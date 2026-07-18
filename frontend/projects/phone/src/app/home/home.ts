import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="card">
        <h1 style="text-align:center">KaraoGUI</h1>
        <div class="btn-stack">
          <a routerLink="/create" class="btn btn-primary" style="text-align:center;text-decoration:none">
            Create a game
          </a>
          <a routerLink="/join" class="btn btn-secondary" style="text-align:center;text-decoration:none">
            Join a game
          </a>
        </div>
      </div>
    </div>
  `,
})
export class HomeComponent {}
