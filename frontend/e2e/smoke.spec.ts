import { test, expect } from '@playwright/test';

/**
 * Smoke E2E (SPEC-0028 / slice 12-3): proves the isolated stack is up and serving — the SPA loads the
 * login screen and the backend answers through the same-origin /api proxy. This is the cheapest signal
 * that compose.e2e.yaml (ephemeral DB on 4201/8081) works before the heavier journeys run.
 */
test('the app serves the login screen on the isolated stack', async ({ page }) => {
  await page.goto('/login');
  // The login renders the brand and the SSO sign-in button (real i18n labels), so the SPA bundle
  // loaded. Login is delegated to the IdP now (OIDC — Phase 13/DL-0106).
  await expect(page.getByText('Acme Travel ERP')).toBeVisible();
  await expect(page.getByTestId('login-sso')).toBeVisible();
});

test('the backend health endpoint answers through the proxy (readiness)', async ({ request }) => {
  const response = await request.get('/api/system/health');
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.status).toBe('UP');
});

test('GET /api/version answers through the proxy (build metadata, public)', async ({ request }) => {
  const response = await request.get('/api/version');
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  expect(body.version).toMatch(/\d+\.\d+\.\d+/);
});
