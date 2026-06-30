import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

/** The two supported UI themes (SPEC-0026 BR3, DL-0091). */
export type Theme = 'light' | 'dark';

const THEME_KEY = 'acme.erp.theme';
const DARK_CLASS = 'app-dark';

/**
 * Light/dark theme state (SPEC-0026 BR3, DL-0091). Toggles the `.app-dark` class on the document
 * element — the same selector the PrimeNG Aura preset uses as its `darkModeSelector` — so PrimeNG
 * components and the shell tokens (`--app-*`) switch together. The choice is persisted in
 * localStorage; when nothing is stored the OS preference (`prefers-color-scheme`) wins.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly themeSignal = signal<Theme>(this.resolveInitial());

  /** The active theme. */
  readonly theme = this.themeSignal.asReadonly();
  /** Whether the dark theme is active. */
  readonly isDark = computed(() => this.themeSignal() === 'dark');

  constructor() {
    this.apply(this.themeSignal());
  }

  /** Switches to the given theme, applies it to the document and persists the choice. */
  setTheme(theme: Theme): void {
    this.themeSignal.set(theme);
    this.apply(theme);
    this.persist(theme);
  }

  /** Flips between light and dark. */
  toggle(): void {
    this.setTheme(this.themeSignal() === 'dark' ? 'light' : 'dark');
  }

  private apply(theme: Theme): void {
    const root = this.document.documentElement;
    if (theme === 'dark') {
      root.classList.add(DARK_CLASS);
    } else {
      root.classList.remove(DARK_CLASS);
    }
  }

  private resolveInitial(): Theme {
    const stored = this.readStored();
    if (stored) {
      return stored;
    }
    // No explicit choice yet: follow the OS preference (DL-0091).
    const prefersDark =
      typeof this.docDefaultView?.matchMedia === 'function' &&
      this.docDefaultView.matchMedia('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'dark' : 'light';
  }

  private get docDefaultView(): (Window & typeof globalThis) | null {
    return this.document.defaultView;
  }

  private readStored(): Theme | null {
    try {
      const raw = this.docDefaultView?.localStorage.getItem(THEME_KEY);
      return raw === 'light' || raw === 'dark' ? raw : null;
    } catch {
      return null;
    }
  }

  private persist(theme: Theme): void {
    try {
      this.docDefaultView?.localStorage.setItem(THEME_KEY, theme);
    } catch {
      // Ignore storage failures (private mode, etc.).
    }
  }
}
