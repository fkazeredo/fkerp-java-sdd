import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api';
import { AuthUser, LoginCommand, LoginResult } from './auth.models';

const TOKEN_KEY = 'acme.erp.token';
const USER_KEY = 'acme.erp.user';

/**
 * Holds the authentication state on the client (SPEC-0024). It calls `POST /api/identity/login`,
 * persists the bearer token and the resolved user in localStorage (so a refresh keeps the session),
 * and exposes the current user as a signal for the shell/guard. The backend remains the single
 * authorization authority — this state is only mirrored for display and routing; it never decides
 * access on its own.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly userSignal = signal<AuthUser | null>(readStoredUser());
  private tokenValue: string | null = readStoredToken();

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
      }),
    );
  }

  /** Clears the local session. */
  logout(): void {
    this.tokenValue = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.userSignal.set(null);
  }

  /** Whether the current user holds the given role. */
  hasRole(role: string): boolean {
    return this.userSignal()?.roles.includes(role) ?? false;
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
