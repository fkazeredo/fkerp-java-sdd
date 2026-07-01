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

/** A user holding every role — sees the full navigation, including the role-gated items (SPEC-0029). */
const SUPERUSER: AuthUser = {
  userId: 'u0',
  username: 'dev',
  displayName: 'Diretor',
  roles: ['ROLE_DIRECTOR', 'ROLE_FINANCE', 'ROLE_OPERATIONS', 'ROLE_IT', 'ROLE_POLICY_ADMIN'],
};

describe('Shell', () => {
  it('renders the full navigation for a user with every role (AC2)', () => {
    configure(SUPERUSER, {});
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const links = fixture.nativeElement.querySelectorAll('.shell__nav-item');
    expect(links.length).toBe(NAV_ITEMS.length);
    expect(fixture.nativeElement.querySelector('[data-testid="shell-user"]').textContent).toContain(
      'Diretor',
    );
  });

  it('hides role-gated items from a user without the role (SPEC-0029 BR/DL-0109)', () => {
    configure(DIRECTOR, {});
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    const visible = NAV_ITEMS.filter(
      (item) => !item.roles || item.roles.some((role) => DIRECTOR.roles.includes(role)),
    ).length;
    const links = fixture.nativeElement.querySelectorAll('.shell__nav-item');
    expect(links.length).toBe(visible);
    expect(links.length).toBeLessThan(NAV_ITEMS.length);
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
