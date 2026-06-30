import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { Observable, catchError, map, of } from 'rxjs';
import { API_BASE_URL } from '../config/api';
import { AuthUser } from './auth.models';
import { buildAuthConfig } from './oidc.config';

/**
 * Holds the authentication state on the client (SPEC-0024 — graduated to OIDC in Phase 13, DL-0106).
 * It delegates the login to the external IdP (Keycloak) via Authorization Code + PKCE
 * (`angular-oauth2-oidc`) and renews the access token through a refresh token (real silent-refresh,
 * graduating the `/me`-revalidation stopgap of DL-0092). The backend remains the single authorization
 * authority — this state is only mirrored (from the verified token's claims, confirmed by `GET /me`)
 * for display and routing; it never decides access on its own.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly http = inject(HttpClient);

  private readonly userSignal = signal<AuthUser | null>(null);

  /** The current user, or null when logged out. */
  readonly user = this.userSignal.asReadonly();
  /** Whether a user is authenticated (a valid access token resolved into a user). */
  readonly isAuthenticated = computed(() => this.userSignal() !== null);

  /** The current bearer access token (used by the auth interceptor), or null. */
  token(): string | null {
    return this.oauth.getAccessToken() || null;
  }

  /**
   * Configures the OIDC client and completes the login if the browser is returning from the IdP with
   * an authorization code. Runs once at app startup (app.config). Real silent-refresh is enabled so
   * the access token is renewed via the refresh token before it expires. Resolves to the current user
   * (or null). Never throws — a failure leaves the session logged out.
   */
  async bootstrapSession(): Promise<AuthUser | null> {
    this.oauth.configure(buildAuthConfig());
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
    } catch {
      this.userSignal.set(null);
      return null;
    }
    if (this.oauth.hasValidAccessToken()) {
      this.oauth.setupAutomaticSilentRefresh();
      // Confirm the token with the backend (the single authority) and record the AUTH_LOGIN audit.
      return new Promise((resolve) => this.verifySession().subscribe((u) => resolve(u)));
    }
    this.userSignal.set(null);
    return null;
  }

  /** Starts the OIDC login (redirects to the IdP). `returnUrl` is preserved as router state. */
  login(returnUrl?: string): void {
    this.oauth.initLoginFlow(returnUrl ?? '/');
  }

  /** Logs out locally and at the IdP (ends the SSO session), then returns to the app root. */
  logout(): void {
    this.userSignal.set(null);
    this.oauth.logOut();
  }

  /**
   * Clears the local session WITHOUT redirecting to the IdP (used by the 401 handler): drops the
   * tokens and the mirrored user so the guard sends the user to the login screen, where a fresh
   * OIDC login can start. Avoids an aggressive IdP round-trip on a transient 401.
   */
  clearLocalSession(): void {
    this.userSignal.set(null);
    this.oauth.logOut(true); // noRedirectToLogoutUrl = true → local-only token revocation
  }

  /** Whether the current user holds the given role. */
  hasRole(role: string): boolean {
    return this.userSignal()?.roles.includes(role) ?? false;
  }

  /**
   * Validates the current session against the backend via `GET /me` (the backend is the authority).
   * On 200 it mirrors the verified user; on any error it clears the session. Emits the resolved user
   * or null. It is also the post-login identity bootstrap, so the backend records the AUTH_LOGIN
   * audit on this call.
   */
  verifySession(): Observable<AuthUser | null> {
    if (!this.token()) {
      this.userSignal.set(null);
      return of(null);
    }
    return this.http.get<AuthUser>(`${API_BASE_URL}/identity/me`).pipe(
      map((user) => {
        this.userSignal.set(user);
        return user as AuthUser | null;
      }),
      catchError(() => {
        this.userSignal.set(null);
        return of(null);
      }),
    );
  }
}
