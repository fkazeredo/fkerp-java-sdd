import { test, expect } from '@playwright/test';

/**
 * Auth-guard sad path (SPEC-0028 AC5). Visiting a protected route without a session redirects to the
 * login screen, preserving the attempted URL as `returnUrl` (authGuard / SPEC-0026 BR7). The guard is
 * a UX convenience — the backend still enforces real authorization on every API call.
 */
test('a protected route without a session redirects to /login with returnUrl', async ({ page }) => {
  // No login performed: localStorage has no token, so the guard must bounce us to /login.
  await page.goto('/accounts');
  await expect(page).toHaveURL(/\/login(\?|$)/);
  await expect(page).toHaveURL(/returnUrl=%2Faccounts/);
  await expect(page.locator('#login-username')).toBeVisible();
});
