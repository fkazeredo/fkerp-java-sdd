import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * Login journey (SPEC-0028 AC3/AC4 — graduated to OIDC in Phase 13). Happy path: a seeded Keycloak
 * user signs in through the real OIDC flow (SSO button → IdP hosted login → redirect back) and lands
 * on the authenticated dashboard. Sad path: wrong credentials are rejected on the IdP's login page,
 * which never reveals whether the user exists (the IdP shows a generic error).
 */
test('signs in with a seeded user via OIDC and lands on the dashboard', async ({ page }) => {
  await login(page);
  // Keycloak appends an `?iss=` query param to the redirect; the route is still /dashboard.
  await expect(page).toHaveURL(/\/dashboard(\?|$)/);
  // The shell shows the signed-in user.
  await expect(page.getByTestId('shell-user')).toBeVisible();
});

test('wrong credentials are rejected on the IdP login page', async ({ page }) => {
  await page.goto('/login');
  await page.getByTestId('login-sso').click();
  await page.waitForURL(/\/realms\/acme\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill('director');
  await page.locator('#password').fill('wrong-password');
  await page.locator('#kc-login').click();
  // The IdP shows a generic error and stays on its own pages (login-actions / auth) — the app is
  // NOT reached (no redirect back to 4201).
  await expect(page.locator('#input-error, .kc-feedback-text, .alert-error')).toBeVisible();
  await expect(page).toHaveURL(/localhost:8089\/realms\/acme\//);
});
