import { AuthConfig } from 'angular-oauth2-oidc';

/**
 * OIDC configuration for the SELF-HOSTED Authorization Server (SPEC-0024 — re-graduated in Phase 17,
 * ADR-0018 / DL-0113). Keycloak was removed; OIDC is now served by the Spring Authorization Server
 * embedded in the backend app. The SPA logs in with Authorization Code + PKCE (a public client, no
 * secret) against that same app. The backend remains the single authorization authority — this
 * config only drives the browser login flow.
 *
 * <p>The issuer is the backend's own origin, derived at runtime from the frontend's port so the same
 * build works in dev (4200 → backend 8080) and in the isolated E2E stack (4201 → backend 8081)
 * without a rebuild — matching the docker-compose wiring (DL-0114). Override via {@link
 * resolveIssuer}'s host when serving elsewhere.
 *
 * <p><strong>Silent-refresh (DL-0113):</strong> the Spring Authorization Server does not issue refresh
 * tokens to a public client, so the access token is renewed via a hidden <em>silent iframe</em>
 * ({@code prompt=none} against the AS SSO session) instead of a refresh token — the mechanism
 * `angular-oauth2-oidc` supports for SPAs without a refresh token.
 */
const E2E_FRONTEND_PORT = '4201';
const DEV_BACKEND_PORT = '8080';
const E2E_BACKEND_PORT = '8081';

/** The self-hosted AS issuer URL for the current environment (the backend origin). */
export function resolveIssuer(location: Location = window.location): string {
  const backendPort = location.port === E2E_FRONTEND_PORT ? E2E_BACKEND_PORT : DEV_BACKEND_PORT;
  return `${location.protocol}//${location.hostname}:${backendPort}`;
}

/** Builds the {@link AuthConfig} for the current environment. */
export function buildAuthConfig(location: Location = window.location): AuthConfig {
  return {
    issuer: resolveIssuer(location),
    redirectUri: location.origin + '/',
    postLogoutRedirectUri: location.origin + '/',
    silentRefreshRedirectUri: location.origin + '/silent-refresh.html',
    clientId: 'acme-erp-web',
    responseType: 'code',
    scope: 'openid profile',
    // PKCE is on by default for code flow; be explicit and never use the deprecated implicit flow.
    requireHttps: false, // dev/E2E run over http://localhost — production must serve the AS over https
    // The self-hosted AS does not issue refresh tokens to a public client (DL-0113): renew the access
    // token via a hidden silent iframe (prompt=none) against the AS SSO session.
    useSilentRefresh: true,
    showDebugInformation: false,
    // Renew the access token a bit before it expires (silent iframe).
    timeoutFactor: 0.75,
    clearHashAfterLogin: true,
  };
}
