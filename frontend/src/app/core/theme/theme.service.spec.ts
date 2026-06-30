import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

const THEME_KEY = 'acme.erp.theme';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('app-dark');
  });

  function create(): ThemeService {
    TestBed.configureTestingModule({});
    return TestBed.inject(ThemeService);
  }

  it('applies and removes the .app-dark class when toggling (AC3)', () => {
    const theme = create();
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);

    theme.setTheme('dark');
    expect(theme.isDark()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);

    theme.setTheme('light');
    expect(theme.isDark()).toBe(false);
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);
  });

  it('persists the chosen theme to localStorage (AC3)', () => {
    const theme = create();
    theme.setTheme('dark');
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');

    theme.toggle();
    expect(localStorage.getItem(THEME_KEY)).toBe('light');
  });

  it('restores a stored theme on construction (AC3)', () => {
    localStorage.setItem(THEME_KEY, 'dark');
    const theme = create();
    expect(theme.isDark()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
  });

  it('follows the OS preference when nothing is stored (AC3)', () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) =>
      ({
        matches: query.includes('dark'),
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList) as typeof window.matchMedia;
    try {
      const theme = create();
      expect(theme.isDark()).toBe(true);
    } finally {
      window.matchMedia = original;
    }
  });
});
