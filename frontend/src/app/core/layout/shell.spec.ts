import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AuthService } from '../auth/auth.service';
import { AuthUser } from '../auth/auth.models';
import { InMemoryTranslateLoader } from '../i18n/in-memory-translate.loader';
import { ThemeService } from '../theme/theme.service';
import { NAV_ITEMS } from './nav';
import { Shell } from './shell';

function configure(user: AuthUser | null, theme: Partial<ThemeService>): void {
  const userSignal = signal<AuthUser | null>(user);
  TestBed.configureTestingModule({
    imports: [Shell],
    providers: [
      provideRouter([]),
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      {
        provide: AuthService,
        useValue: { user: userSignal.asReadonly(), logout: vi.fn() },
      },
      {
        provide: ThemeService,
        useValue: { isDark: signal(false).asReadonly(), toggle: vi.fn(), ...theme },
      },
    ],
  });
}

const DIRECTOR: AuthUser = {
  userId: 'u1',
  username: 'director',
  displayName: 'Diretor',
  roles: ['ROLE_DIRECTOR'],
};

describe('Shell', () => {
  it('renders the full navigation for an authenticated user (AC2)', () => {
    configure(DIRECTOR, {});
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('.shell__nav-item');
    expect(links.length).toBe(NAV_ITEMS.length);
    expect(fixture.nativeElement.querySelector('[data-testid="shell-user"]').textContent).toContain(
      'Diretor',
    );
  });

  it('toggles the theme via the topbar button (AC3)', () => {
    const toggle = vi.fn();
    configure(DIRECTOR, { toggle });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="theme-toggle"]').click();

    expect(toggle).toHaveBeenCalled();
  });

  it('opens and closes the mobile drawer (AC2)', () => {
    configure(DIRECTOR, {});
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    expect(fixture.componentInstance.drawerOpen()).toBe(false);
    fixture.componentInstance.toggleDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBe(true);
    fixture.componentInstance.closeDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBe(false);
  });
});
