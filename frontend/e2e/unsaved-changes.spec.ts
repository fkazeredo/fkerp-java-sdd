import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * Unsaved-changes protection sad path (SPEC-0028 AC7). The canDeactivate guard uses the native
 * confirm() dialog (SPEC-0026 BR9): leaving a dirty form asks for confirmation. Dismissing keeps the
 * user on the page; accepting lets them leave. Playwright intercepts the native dialog.
 */
test('leaving a dirty Accounts form warns; dismissing keeps you on the page', async ({ page }) => {
  await login(page);
  await page.locator('.shell__nav-item', { hasText: 'Contas' }).click();
  await expect(page).toHaveURL(/\/accounts$/);

  // Make the form dirty (isDirty() becomes true once the document field has content).
  await page.locator('#acc-document').fill('123');

  // First attempt to leave: dismiss the confirm → we must stay on /accounts.
  let sawDialog = false;
  page.once('dialog', (dialog) => {
    sawDialog = true;
    expect(dialog.message()).toContain('alterações não salvas');
    void dialog.dismiss();
  });
  await page.locator('.shell__nav-item', { hasText: 'Painel' }).click();
  await expect.poll(() => sawDialog).toBe(true);
  await expect(page).toHaveURL(/\/accounts$/);

  // Second attempt: accept the confirm → navigation proceeds to the dashboard.
  page.once('dialog', (dialog) => void dialog.accept());
  await page.locator('.shell__nav-item', { hasText: 'Painel' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
});
