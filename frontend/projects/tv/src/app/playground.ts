import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-playground',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    // Import components you want to develop/test here
  ],
  templateUrl: './playground.html',
  styleUrl: './playground.scss',
})
export class Playground {}
