import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api';
import { AuthUser, LoginCommand, LoginResult } from './auth.models';

const TOKEN_KEY = 'acme.erp.token';
const USER_KEY = 'acme.erp.user';
/** Revalidate this many seconds before the token expires (DL-0092). */
const REVALIDATE_LEAD_SECONDS = 60;

/**
 * Holds the authentication state on the client (SPEC-0024). It calls `POST /api/identity/login`,
 * persists the bearer token and the resolved user in localStorage (so a refresh keeps the session),
 * and exposes the current user as a signal for the shell/guard. The backend remains the single
 * authorization authority — this state is only mirrored for display and routing; it never decides
 * access on its own.
 *
 * <p>Silent session revalidation (SPEC-0026 BR7, DL-0092): there is no refresh token in this phase,
 * so "silent refresh" means revalidating the stored token against the backend via `GET /me` — on
 * boot and shortly before expiry. A 401 clears the session quietly. A real token refresh arrives
 * with the external OIDC issuer in Phase 13.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly userSignal = signal<AuthUser | null>(readStoredUser());
  private tokenValue: string | null = readStoredToken();
  private revalidateTimer: ReturnType<typeof setTimeout> | null = null;

  /** The current user, or null when logged out. */
  readonly user = this.userSignal.asReadonly();
  /** Whether a user is authenticated. */
  readonly isAuthenticated = computed(() => this.userSignal() !== null);

  /** The current bearer token (used by the auth interceptor), or null. */
  token(): string | null {
    return this.tokenValue;
  }

  /** Authenticates and stores the session; surfaces the normalized ApiError on failure. */
  login(command: LoginCommand): Observable<LoginResult> {
    return this.http.post<LoginResult>(`${API_BASE_URL}/identity/login`, command).pipe(
      tap((result) => {
        this.tokenValue = result.token;
        localStorage.setItem(TOKEN_KEY, result.token);
        localStorage.setItem(USER_KEY, JSON.stringify(result.user));
        this.userSignal.set(result.user);
        this.scheduleRevalidation(result.expiresIn);
      }),
    );
  }

  /**
   * Called once at app startup (DL-0092). If a token is stored, revalidate it silently against the
   * backend so the mirrored user comes from the verified token, not from localStorage; otherwise
   * there is nothing to do. Never throws — a failure just clears the session.
   */
  bootstrapSession(): void {
    if (!this.tokenValue) {
      return;
    }
    this.verifySession().subscribe();
  }

  /**
   * Revalidates the current token via `GET /me`. On 200, refreshes the mirrored user from the
   * backend's verified response and (re)schedules the next revalidation. On any error (e.g. 401),
   * clears the session. Emits the resolved user or null. Safe to call repeatedly.
   */
  verifySession(): Observable<AuthUser | null> {
    if (!this.tokenValue) {
      return of(null);
    }
    return this.http.get<AuthUser>(`${API_BASE_URL}/identity/me`).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.userSignal.set(user);
      }),
      catchError(() => {
        this.logout();
        return of(null);
      }),
    );
  }

  /** Clears the local session and cancels any pending revalidation. */
  logout(): void {
    if (this.revalidateTimer) {
      clearTimeout(this.revalidateTimer);
      this.revalidateTimer = null;
    }
    this.tokenValue = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.userSignal.set(null);
  }

  /** Whether the current user holds the given role. */
  hasRole(role: string): boolean {
    return this.userSignal()?.roles.includes(role) ?? false;
  }

  /** Schedules a silent `/me` revalidation shortly before the token expires (DL-0092). */
  private scheduleRevalidation(expiresInSeconds: number): void {
    if (this.revalidateTimer) {
      clearTimeout(this.revalidateTimer);
      this.revalidateTimer = null;
    }
    const delaySeconds = Math.max(expiresInSeconds - REVALIDATE_LEAD_SECONDS, 1);
    // Guard against absurd values (and tests that run with fake timers won't schedule real work).
    if (!Number.isFinite(delaySeconds)) {
      return;
    }
    this.revalidateTimer = setTimeout(() => {
      this.verifySession().subscribe();
    }, delaySeconds * 1000);
  }
}

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function readStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    return null;
  }
}
