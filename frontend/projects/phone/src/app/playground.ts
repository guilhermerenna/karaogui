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
        <span class="playground-badge">Phone Playground</span>
        <span class="playground-hint">
          Edit <code>projects/phone/src/app/playground.ts</code> — set
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
        flex-wrap: wrap;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.75rem;
        background: #fff;
        border-bottom: 1px solid #e5e7eb;
        font-size: 0.75rem;
        color: #4b5563;
      }

      .playground-badge {
        background: #6366f1;
        color: #fff;
        font-weight: 700;
        padding: 0.15rem 0.5rem;
        border-radius: 4px;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        font-size: 0.65rem;
      }

      .playground-header code {
        color: #4f46e5;
        background: rgba(99, 102, 241, 0.1);
        padding: 0.1rem 0.3rem;
        border-radius: 4px;
      }

      .playground-stage {
        flex: 1;
        padding: 1rem;
      }
    `,
  ],
})
export class Playground {}
