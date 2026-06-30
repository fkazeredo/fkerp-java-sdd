import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { AuthUser } from './auth.models';

const TOKEN_KEY = 'acme.erp.token';
const USER_KEY = 'acme.erp.user';

const USER: AuthUser = {
  userId: 'u1',
  username: 'director',
  displayName: 'Diretor',
  roles: ['ROLE_DIRECTOR'],
};

describe('AuthService — silent session revalidation (DL-0092)', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(TOKEN_KEY, 'stored-jwt');
    localStorage.setItem(USER_KEY, JSON.stringify(USER));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  it('keeps the session and refreshes the user when /me returns 200 (AC7)', () => {
    let emitted: AuthUser | null | undefined;
    auth.verifySession().subscribe((u) => (emitted = u));

    const req = http.expectOne('/api/identity/me');
    expect(req.request.method).toBe('GET');
    const refreshed = { ...USER, displayName: 'Diretor Geral' };
    req.flush(refreshed);

    expect(emitted?.displayName).toBe('Diretor Geral');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.user()?.displayName).toBe('Diretor Geral');
  });

  it('clears the session when /me returns 401 (AC7)', () => {
    auth.verifySession().subscribe();

    http.expectOne('/api/identity/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('bootstrapSession revalidates when a token is stored (AC7)', () => {
    auth.bootstrapSession();
    http.expectOne('/api/identity/me').flush(USER);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('bootstrapSession does nothing without a stored token', () => {
    auth.logout(); // clears the token
    auth.bootstrapSession();
    http.expectNone('/api/identity/me');
  });
});
