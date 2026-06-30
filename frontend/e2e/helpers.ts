import { expect, Page } from '@playwright/test';

/**
 * Shared E2E helpers (SPEC-0028). Not a spec file, so it can be imported by the journeys (Playwright
 * forbids importing one *.spec.ts from another).
 *
 * `director`/`dev12345` is a dev-seed user; it exists because the E2E backend runs the `dev` profile
 * (DL-0101). Signs in and waits until the authenticated dashboard is shown.
 */
export async function login(
  page: Page,
  username = 'director',
  password = 'dev12345',
): Promise<void> {
  await page.goto('/login');
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Painel' })).toBeVisible();
}
