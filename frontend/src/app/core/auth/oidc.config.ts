import { AuthConfig } from 'angular-oauth2-oidc';

/**
 * OIDC configuration for the external IdP (SPEC-0024 Phase 13 / DL-0106). The SPA logs in against
 * Keycloak with Authorization Code + PKCE (a public client, no secret) and renews the access token
 * via a refresh token (real silent-refresh). The backend remains the single authorization authority
 * — this config only drives the browser login flow.
 *
 * <p>The issuer is derived at runtime from the frontend's port so the same build works in dev (4200 →
 * Keycloak 8088) and in the isolated E2E stack (4201 → Keycloak 8089) without a rebuild — matching the
 * docker-compose wiring (DL-0103). Override via {@link resolveIssuer}'s host when serving elsewhere.
 */
const E2E_FRONTEND_PORT = '4201';
const DEV_KEYCLOAK_PORT = '8088';
const E2E_KEYCLOAK_PORT = '8089';
const REALM_PATH = '/realms/acme';

/** The Keycloak issuer URL for the current environment (derived from the frontend port). */
export function resolveIssuer(location: Location = window.location): string {
  const kcPort = location.port === E2E_FRONTEND_PORT ? E2E_KEYCLOAK_PORT : DEV_KEYCLOAK_PORT;
  return `${location.protocol}//${location.hostname}:${kcPort}${REALM_PATH}`;
}

/** Builds the {@link AuthConfig} for the current environment. */
export function buildAuthConfig(location: Location = window.location): AuthConfig {
  return {
    issuer: resolveIssuer(location),
    redirectUri: location.origin + '/',
    postLogoutRedirectUri: location.origin + '/',
    clientId: 'acme-erp-web',
    responseType: 'code',
    scope: 'openid profile offline_access',
    // PKCE is on by default for code flow; be explicit and never use the deprecated implicit flow.
    requireHttps: false, // dev/E2E run over http://localhost — production must serve the IdP over https
    useSilentRefresh: false, // refresh-token rotation (offline_access) drives silent renewal
    showDebugInformation: false,
    // Renew the access token a bit before it expires using the refresh token (real silent-refresh).
    timeoutFactor: 0.75,
    clearHashAfterLogin: true,
  };
}
