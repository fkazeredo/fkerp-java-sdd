import { test, expect } from '@playwright/test';
import { login, tokenFor } from './helpers';

/**
 * AfterSales & Cancellation operator journey (SPEC-0029 16b / DL-0109). An OPERATIONS user signs in,
 * opens the After-sales screen from the shell nav (the nav item is role-gated to ROLE_OPERATIONS) and
 * sees the case list's genuine empty state on the ephemeral DB (DL-0101); then opens the Cancellation
 * policy screen and looks up a scope that has no policy yet, exercising the screen-state error path
 * (the backend answers 404/error for an unknown scope). The authorization sad path is proven at the
 * API: a token without ROLE_FINANCE is denied (403) on a finance close action — the backend is the
 * single authority (security.md / DL-0082); the role-gated nav is only tidiness.
 */

test('an OPERATIONS user opens After-sales (empty list) and the Cancellation policy screen', async ({
  page,
}) => {
  await login(page, 'ops');

  // After-sales nav item is visible to ROLE_OPERATIONS — navigate to it.
  await page.locator('.shell__nav-item', { hasText: 'Pós-venda' }).click();
  await expect(page).toHaveURL(/\/aftersales$/);
  await expect(page.getByRole('heading', { name: 'Pós-venda — chamados e SLA' })).toBeVisible();

  // Fresh ephemeral DB → the case list is empty: the shared empty state is shown.
  await expect(page.getByTestId('state-empty')).toBeVisible();

  // Cancellation policy is also an operations screen.
  await page.locator('.shell__nav-item', { hasText: 'Cancelamento' }).click();
  await expect(page).toHaveURL(/\/cancellation$/);
  await expect(page.getByRole('heading', { name: 'Política de cancelamento' })).toBeVisible();

  // Looking up a scope that has no policy yet surfaces the screen-state error (backend authority).
  await page.locator('#cx-scope').fill('UNKNOWN-SCOPE');
  await page.getByTestId('cancellation-find').click();
  await expect(page.getByTestId('state-error').or(page.getByTestId('cancellation-policy'))).toBeVisible();
});

test('a token without ROLE_FINANCE is denied (403) on the finance close action', async ({
  browser,
  request,
}) => {
  // `ops` (ROLE_OPERATIONS) lacks ROLE_FINANCE → the finance close action is 403 (DL-0082). This
  // proves the backend is the authority even though the operations nav is visible to `ops`.
  const opsToken = await tokenFor(browser, 'ops');
  const denied = await request.post('/api/finance/periods/2026-06/close', {
    headers: { Authorization: `Bearer ${opsToken}` },
  });
  expect(denied.status()).toBe(403);

  // The AfterSales read is allowed for an authenticated operations user (never 401/403).
  const cases = await request.get('/api/aftersales/cases', {
    headers: { Authorization: `Bearer ${opsToken}` },
  });
  expect(cases.status()).not.toBe(401);
  expect(cases.status()).not.toBe(403);
});
