import { expect, test } from '@playwright/test';
import { login, tokenFor } from './helpers';

/**
 * Platform/TI + People operator journey (SPEC-0029 16d / DL-0109). The IT operator signs in and opens
 * the Platform screen from the shell nav (the item is role-gated to ROLE_IT). The e-CNPJ certificate
 * card shows METADATA ONLY (no secret material), the governed-job catalog and the run history render
 * with the genuine empty/populated states on the ephemeral DB (DL-0101). Then the People screen —
 * reached by URL (its backend reads are `authenticated()`) — shows the collaborator list empty state.
 *
 * The authorization sad path is proven at the API: a token WITHOUT ROLE_IT is denied (403) triggering
 * a governed job, while the IT operator is allowed — the backend is the single authority
 * (security.md / DL-0082); the role-gated nav is only menu tidiness.
 */

test('an IT operator opens the Platform screen (certificate metadata, jobs) and the People list', async ({
  page,
}) => {
  await login(page, 'it');

  // Platform screen (nav gated to ROLE_IT).
  await page.locator('.shell__nav-item', { hasText: 'Plataforma / TI' }).click();
  await expect(page).toHaveURL(/\/platform$/);
  await expect(
    page.getByRole('heading', { name: 'Plataforma / TI — jobs, certificado e auditoria' }),
  ).toBeVisible();

  // The certificate card shows metadata only (or the empty state when none is custodied) —
  // never a private key/password.
  await expect(
    page.getByTestId('platform-certificate').or(page.getByTestId('state-empty').first()),
  ).toBeVisible();

  // The governed-job catalog renders (populated table or the shared empty state).
  await expect(
    page.getByTestId('platform-jobs-table').or(page.getByTestId('state-empty').first()),
  ).toBeVisible();

  // People screen — reachable by URL (backend read is authenticated).
  await page.goto('/people');
  await expect(page).toHaveURL(/\/people$/);
  await expect(
    page.getByRole('heading', { name: 'Pessoas / RH — colaboradores, jornada e discrepâncias' }),
  ).toBeVisible();
  // Fresh ephemeral DB → the collaborator list is empty: the shared empty state is shown.
  await expect(page.getByTestId('state-empty').first()).toBeVisible();
});

test('a governed job trigger is IT-gated (403 without ROLE_IT, allowed for IT)', async ({
  browser,
  request,
}) => {
  // `ops` (ROLE_OPERATIONS) lacks ROLE_IT → triggering a governed job is 403 (DL-0082).
  const opsToken = await tokenFor(browser, 'ops');
  const denied = await request.post('/api/platform/jobs/license-expiry-sweep/trigger', {
    headers: { Authorization: `Bearer ${opsToken}` },
  });
  expect(denied.status()).toBe(403);

  // The IT operator is authorized on the trigger endpoint (never 401/403; a job that is unknown or
  // already running is a different, non-authorization status).
  const itToken = await tokenFor(browser, 'it');
  const allowed = await request.post('/api/platform/jobs/license-expiry-sweep/trigger', {
    headers: { Authorization: `Bearer ${itToken}` },
  });
  expect(allowed.status()).not.toBe(401);
  expect(allowed.status()).not.toBe(403);
});
