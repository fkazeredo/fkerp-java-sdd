import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * Login journey (SPEC-0028 AC3/AC4 — re-graduated to the self-hosted OIDC in Phase 17). Happy path: a
 * seeded local user signs in through the real OIDC flow (SSO button → the app's own AS `/login` form →
 * redirect back) and lands on the authenticated dashboard. Sad path: wrong credentials are rejected on
 * the AS's login page, which never reveals whether the user exists (a generic error).
 */
test('signs in with a seeded user via OIDC and lands on the dashboard', async ({ page }) => {
  await login(page);
  // The AS appends an `?iss=`/`?code=` param on the redirect; the route is still /dashboard.
  await expect(page).toHaveURL(/\/dashboard(\?|$)/);
  // The shell shows the signed-in user.
  await expect(page.getByTestId('shell-user')).toBeVisible();
});

test('wrong credentials are rejected on the self-hosted AS login page', async ({ page }) => {
  await page.goto('/login');
  await page.getByTestId('login-sso').click();
  await page.waitForURL(/localhost:8081\/login/);
  await page.locator('#username').fill('director');
  await page.locator('#password').fill('wrong-password');
  await page.locator('button[type="submit"]').click();
  // The AS shows a generic error and stays on its own login page (?error) — the app is NOT reached
  // (no redirect back to 4201).
  await expect(page).toHaveURL(/localhost:8081\/login\?error/);
});
