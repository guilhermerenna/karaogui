import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-playground',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    // Import components you want to develop/test here
  ],
  template: `
    <div class="playground-shell">
      <header class="playground-header">
        <span class="playground-badge">TV Playground</span>
        <span class="playground-hint">
          Edit <code>projects/tv/src/app/playground.ts</code> — set
          <code>window.playground = false</code> to exit
        </span>
      </header>

      <main class="playground-stage">
        <!-- Render the component under development here -->
      </main>
    </div>
  `,
  styles: [
    `
      .playground-shell {
        min-height: 100dvh;
        display: flex;
        flex-direction: column;
      }

      .playground-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.5rem 1rem;
        background: #16162a;
        border-bottom: 1px solid #262645;
        font-size: 0.8rem;
        color: #94a3b8;
      }

      .playground-badge {
        background: #6366f1;
        color: #fff;
        font-weight: 700;
        padding: 0.15rem 0.5rem;
        border-radius: 4px;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        font-size: 0.7rem;
      }

      .playground-header code {
        color: #c7d2fe;
        background: rgba(99, 102, 241, 0.15);
        padding: 0.1rem 0.35rem;
        border-radius: 4px;
      }

      .playground-stage {
        flex: 1;
        padding: 1.5rem;
      }
    `,
  ],
})
export class Playground {}
