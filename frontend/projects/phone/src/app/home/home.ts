import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatAppVersion } from 'ui';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
})
export class HomeComponent {
  readonly appVersion = formatAppVersion();
}
