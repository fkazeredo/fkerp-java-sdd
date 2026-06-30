import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginResult } from '../../core/auth/auth.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { LoginPage } from './login-page';

function configure(
  authService: Partial<AuthService>,
  router: Partial<Router>,
  returnUrl: string | null = null,
): void {
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
      { provide: Router, useValue: router },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: { get: () => returnUrl } } },
      },
    ],
  });
}

const RESULT: LoginResult = {
  token: 'jwt',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { userId: 'u1', username: 'finance', displayName: 'Fin', roles: ['ROLE_FINANCE'] },
};

describe('LoginPage', () => {
  it('navigates to the app root after a successful login (AC6)', () => {
    const navigateByUrl = vi.fn();
    configure({ login: () => of(RESULT) }, { navigateByUrl });
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance;
    page.username = 'finance';
    page.password = 'dev12345';

    page.submit();

    expect(page.submitting()).toBe(false);
    expect(page.errorCode()).toBeNull();
    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('honours the returnUrl when present (AC6/BR7)', () => {
    const navigateByUrl = vi.fn();
    configure({ login: () => of(RESULT) }, { navigateByUrl }, '/reconciliation');
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance;
    page.username = 'finance';
    page.password = 'dev12345';

    page.submit();

    expect(navigateByUrl).toHaveBeenCalledWith('/reconciliation');
  });

  it('shows the generic error code when the credentials are rejected (AC6)', () => {
    const navigateByUrl = vi.fn();
    configure(
      {
        login: () =>
          throwError(() => ({ code: 'identity.credentials.invalid', message: '', fields: [] })),
      },
      { navigateByUrl },
    );
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance;
    page.username = 'finance';
    page.password = 'wrong';

    page.submit();

    expect(page.submitting()).toBe(false);
    expect(page.errorCode()).toBe('identity.credentials.invalid');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('stays in the submitting state until the response resolves (AC6)', () => {
    const pending = new Subject<LoginResult>();
    configure({ login: () => pending.asObservable() }, { navigateByUrl: vi.fn() });
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance;
    page.username = 'finance';
    page.password = 'dev12345';

    page.submit();
    expect(page.submitting()).toBe(true);

    pending.next(RESULT);
    pending.complete();
    expect(page.submitting()).toBe(false);
  });

  it('does not submit when fields are empty (AC6)', () => {
    const login = vi.fn(() => of(RESULT));
    configure({ login }, { navigateByUrl: vi.fn() });
    const fixture = TestBed.createComponent(LoginPage);

    fixture.componentInstance.submit();

    expect(login).not.toHaveBeenCalled();
  });
});
