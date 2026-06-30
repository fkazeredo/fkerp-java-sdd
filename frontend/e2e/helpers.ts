import { APIRequestContext, expect, Page } from '@playwright/test';

/**
 * Shared E2E helpers (SPEC-0028 — graduated to OIDC in Phase 13). Not a spec file, so it can be
 * imported by the journeys (Playwright forbids importing one *.spec.ts from another).
 *
 * Login is now a real OIDC Authorization Code + PKCE flow against the dev Keycloak (DL-0103/0106):
 * the SPA's "Entrar com SSO" button redirects to Keycloak's hosted login page, where the seed user
 * (`director`/`dev12345`, etc.) signs in; Keycloak redirects back and the app lands on the dashboard.
 */

/** The dev Keycloak realm reachable from the E2E browser (the frontend runs on 4201 → KC 8089). */
const KEYCLOAK_ISSUER = 'http://localhost:8089/realms/acme';

/** Signs in through the real OIDC flow and waits until the authenticated dashboard is shown. */
export async function login(
  page: Page,
  username = 'director',
  password = 'dev12345',
): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-sso').click();
  // Keycloak's hosted login page (standard HTML form).
  await page.waitForURL(/\/realms\/acme\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  // Back in the app, authenticated.
  await expect(page.getByRole('heading', { name: 'Painel' })).toBeVisible();
}

/**
 * Mints an access token for API-level role checks via Keycloak's token endpoint using the
 * direct-grant E2E client (`acme-e2e-cli`, TEST ONLY). The browser SPA uses code+PKCE; this is only
 * for the Playwright API tests that assert backend authorization directly.
 */
export async function tokenFor(
  request: APIRequestContext,
  username: string,
  password = 'dev12345',
): Promise<string> {
  const res = await request.post(`${KEYCLOAK_ISSUER}/protocol/openid-connect/token`, {
    form: {
      grant_type: 'password',
      client_id: 'acme-e2e-cli',
      username,
      password,
      scope: 'openid profile',
    },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).access_token as string;
}
