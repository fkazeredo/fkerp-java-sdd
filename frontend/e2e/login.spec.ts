import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * Login journey (SPEC-0028 AC3/AC4). Happy path: a seeded dev user signs in and lands on the
 * authenticated dashboard. Sad path: wrong credentials show a generic error and keep the user on the
 * login screen (SPEC-0024 BR4 — the backend never reveals whether the user exists).
 */
test('signs in with a seeded user and lands on the dashboard', async ({ page }) => {
  await login(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  // The shell shows the signed-in user.
  await expect(page.getByTestId('shell-user')).toBeVisible();
});

test('wrong credentials show a generic error and stay on /login', async ({ page }) => {
  await page.goto('/login');
  await page.locator('#login-username').fill('director');
  await page.locator('#login-password').fill('wrong-password');
  await page.getByRole('button', { name: 'Entrar' }).click();
  // Generic credentials error — never reveals whether the username exists (SPEC-0024 BR4).
  await expect(page.getByTestId('login-error')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});
