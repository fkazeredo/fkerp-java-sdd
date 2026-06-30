import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AuthService } from '../../core/auth/auth.service';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { LoginPage } from './login-page';

function configure(authService: Partial<AuthService>, returnUrl: string | null = null): void {
  TestBed.configureTestingModule({
    imports: [LoginPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: AuthService, useValue: authService },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: { get: () => returnUrl } } },
      },
    ],
  });
}

describe('LoginPage (OIDC — DL-0106)', () => {
  it('starts the OIDC login with the app root when no returnUrl (AC6)', () => {
    const login = vi.fn();
    configure({ login });
    const page = TestBed.createComponent(LoginPage).componentInstance;

    page.signIn();

    expect(page.submitting()).toBe(true);
    expect(login).toHaveBeenCalledWith('/');
  });

  it('honours the returnUrl when present (AC6/BR7)', () => {
    const login = vi.fn();
    configure({ login }, '/reconciliation');
    const page = TestBed.createComponent(LoginPage).componentInstance;

    page.signIn();

    expect(login).toHaveBeenCalledWith('/reconciliation');
  });
});
