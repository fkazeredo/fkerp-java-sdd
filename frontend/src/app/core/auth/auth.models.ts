/** Identity contracts (SPEC-0024 — graduated to OIDC in Phase 13). The backend is the single
 * authorization authority; the frontend only mirrors the token's user/roles for display and routing. */

/** The authenticated user as returned by the backend (`GET /me`). */
export interface AuthUser {
  readonly userId: string;
  readonly username: string;
  readonly displayName: string | null;
  readonly roles: readonly string[];
}
