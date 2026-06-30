import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from './auth.service';
import { AuthUser } from './auth.models';

const USER: AuthUser = {
  userId: 'u1',
  username: 'director',
  displayName: 'Diretor',
  roles: ['ROLE_DIRECTOR'],
};

/** A minimal OAuthService stub the AuthService delegates to (SPEC-0024 Phase 13 / DL-0106). */
class OAuthServiceStub {
  accessToken: string | null = 'oidc-access-token';
  validToken = true;
  configure = vi.fn();
  loadDiscoveryDocumentAndTryLogin = vi.fn(() => Promise.resolve(true));
  setupAutomaticSilentRefresh = vi.fn();
  initLoginFlow = vi.fn();
  logOut = vi.fn();
  getAccessToken = vi.fn(() => this.accessToken ?? '');
  hasValidAccessToken = vi.fn(() => this.validToken);
  getIdentityClaims = vi.fn(() => ({ preferred_username: 'director', sub: 'u1' }));
}

describe('AuthService — OIDC session (DL-0106)', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let oauth: OAuthServiceStub;

  beforeEach(() => {
    oauth = new OAuthServiceStub();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OAuthService, useValue: oauth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  it('exposes the access token from the OIDC client', () => {
    expect(auth.token()).toBe('oidc-access-token');
  });

  it('login() starts the OIDC authorization-code flow with the return URL', () => {
    auth.login('/reconciliation');
    expect(oauth.initLoginFlow).toHaveBeenCalledWith('/reconciliation');
  });

  it('bootstrapSession confirms the session against the backend and mirrors the user', async () => {
    const promise = auth.bootstrapSession();
    // bootstrapSession awaits the discovery document before issuing GET /me — let those microtasks
    // settle so the request is in flight, then flush it.
    await Promise.resolve();
    await Promise.resolve();
    http.expectOne('/api/identity/me').flush(USER);
    const user = await promise;

    expect(oauth.setupAutomaticSilentRefresh).toHaveBeenCalled();
    expect(user?.username).toBe('director');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.hasRole('ROLE_DIRECTOR')).toBe(true);
  });

  it('bootstrapSession leaves the session logged out without a valid token', async () => {
    oauth.validToken = false;
    const user = await auth.bootstrapSession();
    expect(user).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('verifySession clears the session when /me returns 401', () => {
    auth.verifySession().subscribe();
    http.expectOne('/api/identity/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('clearLocalSession drops the session without an IdP redirect', () => {
    auth.clearLocalSession();
    expect(oauth.logOut).toHaveBeenCalledWith(true);
    expect(auth.isAuthenticated()).toBe(false);
  });
});
