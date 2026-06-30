import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { LoginResult } from '../../core/auth/auth.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { LoginPage } from './login-page';

function configure(authService: Partial<AuthService>, router: Partial<Router>): void {
  TestBed.configureTestingModule({
    imports: [LoginPage],
    providers: [
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: AuthService, useValue: authService },
      { provide: Router, useValue: router },
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
  it('navigates to the app after a successful login', () => {
    const navigate = vi.fn();
    configure({ login: () => of(RESULT) }, { navigate });
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance;
    page.username = 'finance';
    page.password = 'dev12345';

    page.submit();

    expect(page.submitting()).toBe(false);
    expect(page.errorCode()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/accounts']);
  });

  it('shows the generic error code when the credentials are rejected', () => {
    const navigate = vi.fn();
    configure(
      {
        login: () =>
          throwError(() => ({ code: 'identity.credentials.invalid', message: '', fields: [] })),
      },
      { navigate },
    );
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance;
    page.username = 'finance';
    page.password = 'wrong';

    page.submit();

    expect(page.submitting()).toBe(false);
    expect(page.errorCode()).toBe('identity.credentials.invalid');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('stays in the submitting state until the response resolves', () => {
    const pending = new Subject<LoginResult>();
    configure({ login: () => pending.asObservable() }, { navigate: vi.fn() });
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

  it('does not submit when fields are empty', () => {
    const login = vi.fn(() => of(RESULT));
    configure({ login }, { navigate: vi.fn() });
    const fixture = TestBed.createComponent(LoginPage);

    fixture.componentInstance.submit();

    expect(login).not.toHaveBeenCalled();
  });
});
