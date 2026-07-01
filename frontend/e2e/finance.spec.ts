import { test, expect } from '@playwright/test';
import { login, tokenFor } from './helpers';

/**
 * Finance & Compliance operator journey (SPEC-0029 16a / DL-0109). A FINANCE user signs in, opens the
 * Finance screen from the shell nav (the nav item is role-gated to ROLE_FINANCE) and sees the ledger's
 * genuine empty state on the ephemeral DB (DL-0101); then opens Compliance (visible to any
 * authenticated user) and runs a close-check. The authorization sad path is proven at the API: a
 * non-FINANCE token is denied (403) on the finance close action — the backend is the single authority
 * (security.md / DL-0082); the hidden nav is only tidiness.
 */

test('a FINANCE user opens Finance, sees the empty ledger, and runs a Compliance close-check', async ({
  page,
}) => {
  await login(page, 'finance');

  // Finance nav item is visible to ROLE_FINANCE — navigate to it.
  await page.locator('.shell__nav-item', { hasText: 'Financeiro' }).click();
  await expect(page).toHaveURL(/\/finance$/);
  await expect(page.getByRole('heading', { name: 'Financeiro — razão AP/AR' })).toBeVisible();

  // Fresh ephemeral DB → the ledger is empty: the shared empty state is shown.
  await expect(page.getByTestId('state-empty')).toBeVisible();

  // Compliance is visible to any authenticated user.
  await page.locator('.shell__nav-item', { hasText: 'Conformidade' }).click();
  await expect(page).toHaveURL(/\/compliance$/);
  await expect(page.getByRole('heading', { name: 'Conformidade — cofre e prazos' })).toBeVisible();

  // Run the close-check for a period; an empty period may close (no pending documents).
  await page.locator('#cmp-period').fill('2026-06');
  await page.getByRole('button', { name: 'Verificar' }).click();
  await expect(page.getByTestId('compliance-can-close')).toBeVisible();
});

test('a non-FINANCE token is denied (403) closing a period; a FINANCE token passes the gate', async ({
  request,
}) => {
  // `ops` (ROLE_OPERATIONS) lacks ROLE_FINANCE → the finance close action is 403 (DL-0082).
  const opsToken = await tokenFor(request, 'ops');
  const denied = await request.post('/api/finance/periods/2026-06/close', {
    headers: { Authorization: `Bearer ${opsToken}` },
  });
  expect(denied.status()).toBe(403);

  // The same call with ROLE_FINANCE must NOT be blocked by authorization (proves the gate is
  // role-specific, not a blanket block). The business outcome may vary, but never 401/403.
  const financeToken = await tokenFor(request, 'finance');
  const allowed = await request.post('/api/finance/periods/2026-06/close', {
    headers: { Authorization: `Bearer ${financeToken}` },
  });
  expect(allowed.status()).not.toBe(401);
  expect(allowed.status()).not.toBe(403);
});
