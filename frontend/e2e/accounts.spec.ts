import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * Core business journey (SPEC-0028 AC6) + empty-state edge (BR4). After signing in, the operator
 * navigates to Commercial Accounts via the shell, sees the empty state on a fresh database, registers
 * a commercial account (valid CNPJ), and the new row appears in the list. The ephemeral E2E database
 * starts empty, so the empty state is genuine (DL-0101).
 */
test('navigate to Accounts, see the empty state, then register an account', async ({ page }) => {
  // Registering an account is an operations-desk write (Phase 19a authorization matrix — DL-0119).
  await login(page, 'ops');

  // Navigate from the dashboard to Accounts via the sidebar nav (shell navigation works — AC6).
  await page.locator('.shell__nav-item', { hasText: 'Contas' }).click();
  await expect(page).toHaveURL(/\/accounts$/);
  await expect(page.getByRole('heading', { name: 'Contas comerciais' })).toBeVisible();

  // Fresh ephemeral DB → the list is empty: the shared empty state is shown, not a data table row.
  await expect(page.getByTestId('state-empty')).toBeVisible();
  await expect(page.getByTestId('state-empty')).toHaveText('Nenhuma conta encontrada.');

  // Register a commercial account with a valid CNPJ (digit validation passes on the backend).
  const cnpj = '12345678000195';
  await page.locator('#acc-document').fill(cnpj);
  await page.locator('#acc-displayName').fill('Agência Teste E2E');
  await page.getByRole('button', { name: 'Criar' }).click();

  // The new account shows up in the list (the table replaces the empty state).
  const table = page.getByTestId('accounts-table');
  await expect(table.getByText('Agência Teste E2E')).toBeVisible();
});
