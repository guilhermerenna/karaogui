import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
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
}
