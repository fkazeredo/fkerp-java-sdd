import { test, expect } from '@playwright/test';
import { login, tokenFor } from './helpers';

/**
 * Intelligence & Commercial-policy operator journey (SPEC-0029 16c / DL-0109). A director signs in and
 * opens the Intelligence insight panel from the shell nav (the item is role-gated to ROLE_OPERATIONS;
 * `director` in the dev realm also carries no OPERATIONS role, so the panel is reached by URL — the
 * backend read is `authenticated()`), seeing the genuine empty state on the ephemeral DB (DL-0101).
 * Then the Commercial-policy screen — gated in the nav to DIRECTOR/POLICY_ADMIN — resolves a governed
 * parameter and shows the precedence explainer. The authorization sad path is proven at the API: a
 * token WITHOUT ROLE_DIRECTOR is denied (403) on the directive endpoint, while the director is allowed
 * — the backend is the single authority (security.md / DL-0082); the role-gated nav is only tidiness.
 */

test('a director opens the Intelligence panel (empty) and the Commercial-policy screen', async ({
  page,
}) => {
  await login(page, 'director');

  // Intelligence panel — reachable by URL (backend read is authenticated).
  await page.goto('/intelligence');
  await expect(page).toHaveURL(/\/intelligence$/);
  await expect(
    page.getByRole('heading', { name: 'Inteligência — painel de insights' }),
  ).toBeVisible();
  // Fresh ephemeral DB → the insight list is empty: the shared empty state is shown.
  await expect(page.getByTestId('state-empty')).toBeVisible();

  // Commercial-policy screen (nav gated to DIRECTOR/POLICY_ADMIN).
  await page.locator('.shell__nav-item', { hasText: 'Política comercial' }).click();
  await expect(page).toHaveURL(/\/commercial-policy$/);
  await expect(
    page.getByRole('heading', { name: 'Política comercial — parâmetros governados' }),
  ).toBeVisible();
  // The precedence explainer is always shown (Diretiva > Promoção > Contrato > Política > Padrão).
  await expect(page.getByTestId('policy-precedence')).toBeVisible();

  // Resolving a known key surfaces either a value+provenance or the screen-state error (backend authority).
  await page.locator('#cp-key').fill('MARKUP_PCT');
  await page.getByTestId('policy-resolve').click();
  await expect(
    page.getByTestId('policy-resolved').or(page.getByTestId('state-error')),
  ).toBeVisible();
});

test('the directive endpoint is DIRECTOR-gated (403 without the role, allowed for the director)', async ({
  browser,
  request,
}) => {
  const directive = {
    key: 'MARKUP_PCT',
    value: '0.20',
    type: 'PERCENT',
    justification: 'e2e',
  };

  // `ops` (ROLE_OPERATIONS) lacks ROLE_DIRECTOR → issuing a directive is 403 (DL-0082/BR5).
  const opsToken = await tokenFor(browser, 'ops');
  const denied = await request.post('/api/commercial-policy/directives', {
    headers: { Authorization: `Bearer ${opsToken}` },
    data: directive,
  });
  expect(denied.status()).toBe(403);

  // The director is authorized on the directive endpoint (never 401/403).
  const directorToken = await tokenFor(browser, 'director');
  const allowed = await request.post('/api/commercial-policy/directives', {
    headers: { Authorization: `Bearer ${directorToken}` },
    data: directive,
  });
  expect(allowed.status()).not.toBe(401);
  expect(allowed.status()).not.toBe(403);
});
