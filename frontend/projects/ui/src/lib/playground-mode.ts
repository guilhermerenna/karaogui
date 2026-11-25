import { DestroyRef, inject, isDevMode, Signal, signal } from '@angular/core';

declare global {
  interface Window {
    playground?: boolean;
  }
}

const STORAGE_KEY = 'karaogui:playground';

function readStoredFlag(): boolean {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

function writeStoredFlag(enabled: boolean): void {
  try {
    if (enabled) {
      window.sessionStorage.setItem(STORAGE_KEY, 'true');
    } else {
      window.sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Ignore storage errors in restricted contexts
  }
}

/**
 * Tracks `window.playground` as a reactive Angular signal.
 *
 * - Set `window.playground = true` before bootstrap (e.g. in `index.html`) or live
 *   in the browser DevTools console to enter Playground mode immediately.
 * - Setting `window.playground = true` at runtime persists in `sessionStorage` for
 *   the current tab so dev-server reloads stay in Playground mode.
 * - Set `window.playground = false` to exit Playground mode and load the real app.
 */
export function usePlaygroundMode(): Signal<boolean> {
  if (!isDevMode()) {
    return signal(false).asReadonly();
  }
  const win = typeof window !== 'undefined' ? window : undefined;
  if (!win) {
    return signal(false).asReadonly();
  }

  const previousDescriptor = Object.getOwnPropertyDescriptor(win, 'playground');
  const preExistingValue = win.playground;
  const initial = preExistingValue !== undefined ? Boolean(preExistingValue) : readStoredFlag();
  const active = signal(initial);

  Object.defineProperty(win, 'playground', {
    configurable: true,
    enumerable: true,
    get: () => active(),
    set: (value: unknown) => {
      const enabled = Boolean(value);
      writeStoredFlag(enabled);
      active.set(enabled);
    },
  });

  const destroyRef = inject(DestroyRef, { optional: true });
  destroyRef?.onDestroy(() => {
    if (previousDescriptor) {
      Object.defineProperty(win, 'playground', previousDescriptor);
    } else {
      delete win.playground;
    }
  });

  return active.asReadonly();
}
