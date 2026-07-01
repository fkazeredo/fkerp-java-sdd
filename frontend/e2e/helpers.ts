import { Browser, expect, Page } from '@playwright/test';

/**
 * Shared E2E helpers (SPEC-0028 — re-graduated to the self-hosted OIDC in Phase 17). Not a spec file,
 * so it can be imported by the journeys (Playwright forbids importing one *.spec.ts from another).
 *
 * Login is a real OIDC Authorization Code + PKCE flow against the SELF-HOSTED Authorization Server
 * embedded in the backend (ADR-0018 / DL-0110..0114) — Keycloak was removed. The SPA's "Entrar com
 * SSO" button redirects to the app's own `/login` form (the Spring Authorization Server default page,
 * fields `#username`/`#password`), where the seed user (`director`/`dev12345`, etc.) signs in; the AS
 * redirects back and the app lands on the dashboard.
 */

/** The self-hosted Authorization Server reachable from the E2E browser (frontend 4201 → backend 8081). */
const AS_ISSUER = 'http://localhost:8081';

/** Signs in through the real OIDC flow and waits until the authenticated dashboard is shown. */
export async function login(
  page: Page,
  username = 'director',
  password = 'dev12345',
): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-sso').click();
  // The self-hosted AS form-login page (Spring Authorization Server default: #username/#password).
  await page.waitForURL(/localhost:8081\/login/);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('button[type="submit"]').click();
  // Back in the app, authenticated.
  await expect(page.getByRole('heading', { name: 'Painel' })).toBeVisible();
}

/**
 * Mints an access token for API-level role checks by driving the real browser OIDC login for the
 * given user and reading the access token the SPA stored (SPEC-0024 Phase 17). The self-hosted
 * Authorization Server (OAuth 2.1) does not offer the Resource Owner Password grant, so — unlike the
 * old Keycloak direct-grant client — the token is obtained through the genuine Authorization Code +
 * PKCE flow in an isolated browser context, then extracted from the SPA's session storage.
 */
export async function tokenFor(
  browser: Browser,
  username: string,
  password = 'dev12345',
): Promise<string> {
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await login(page, username, password);
    // angular-oauth2-oidc keeps the access token in sessionStorage under `access_token`.
    const token = await page.evaluate(() => window.sessionStorage.getItem('access_token'));
    expect(token, `no access token found after OIDC login for ${username}`).toBeTruthy();
    return token as string;
  } finally {
    await context.close();
  }
}

/** The self-hosted AS issuer base URL (exported for journeys that assert against it). */
export const AUTHORIZATION_SERVER_ISSUER = AS_ISSUER;
