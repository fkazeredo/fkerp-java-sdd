/** Identity contracts (SPEC-0024). The backend is the single authorization authority; the frontend
 * only mirrors the token's user/roles for display and routing. */

/** The authenticated user as returned by the backend (`/login` and `/me`). */
export interface AuthUser {
  readonly userId: string;
  readonly username: string;
  readonly displayName: string | null;
  readonly roles: readonly string[];
}

/** The login request body. */
export interface LoginCommand {
  readonly username: string;
  readonly password: string;
}

/** The login response: the bearer token and the resolved user. */
export interface LoginResult {
  readonly token: string;
  readonly tokenType: string;
  readonly expiresIn: number;
  readonly user: AuthUser;
}
