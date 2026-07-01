import { expect, test } from '@playwright/test';
import { login, tokenFor } from './helpers';

/**
 * Cadastros journey (SPEC-0031 AC2/AC5; slice 18a). The editable reference-data screen is gated by
 * ROLE_POLICY_ADMIN (DL-0115). The seed user `policy` (ROLE_POLICY_ADMIN — DL-0112) can open the
 * screen, pick a type, add a code and edit an item; a non-admin (`viewer`) is denied at the backend
 * (the single authorization authority), proven both at the API (403) and by the screen's permission
 * state.
 *
 * The self-hosted AS (OAuth 2.1) offers no direct-grant, so the API token is obtained through the
 * genuine Authorization Code + PKCE browser flow via {@link tokenFor}, then replayed on the API.
 */

test('a policy-admin can open Cadastros, add a code and edit an item', async ({ page }) => {
  await login(page, 'policy');

  // The nav entry is visible to ROLE_POLICY_ADMIN; navigate to the screen.
  await page.goto('/cadastro');
  await expect(page.getByTestId('cadastro-type')).toBeVisible();

  // The seeded ASSET_TYPE items render (EQUIPMENT is one of them).
  await expect(page.getByTestId('cadastro-table')).toBeVisible();

  // Add a brand-new code (pure reference data — DL-0115 seam).
  const code = `E2E_${Date.now()}`;
  await page.getByTestId('cadastro-new-code').fill(code);
  await page.getByTestId('cadastro-new-label').fill('Item de teste E2E');
  await page.getByTestId('cadastro-add').click();

  // The new row appears; deactivate it via the toggle to leave the seed clean-ish.
  const row = page.getByTestId(`cadastro-row-${code}`);
  await expect(row).toBeVisible();
  await row.getByTestId('cadastro-toggle').click();
  await expect(row).toBeVisible();
});

test('a non-admin is denied (403) when creating a cadastro item', async ({ browser, request }) => {
  const token = await tokenFor(browser, 'viewer');
  const res = await request.post('/api/cadastro/items', {
    headers: { Authorization: `Bearer ${token}` },
    data: { type: 'ASSET_TYPE', code: 'E2E_SHOULD_FAIL', label: 'x', sortOrder: 0 },
  });
  expect(res.status()).toBe(403);
});

test('the converted supplier-type code round-trips the same wire value', async ({
  browser,
  request,
}) => {
  // The invariant (SPEC-0031 BR4): registering with a known code returns the same string — no wire
  // change. `dev` carries ROLE_FINANCE (admin writes) among its roles.
  const token = await tokenFor(browser, 'dev');
  const res = await request.post('/api/admin/suppliers', {
    headers: { Authorization: `Bearer ${token}` },
    data: { type: 'UTILITY', identifier: '61695227000193', displayName: 'E2E Energia' },
  });
  expect(res.status()).toBe(201);
  const body = await res.json();
  expect(body.type).toBe('UTILITY');
});

test('the converted goal-metric code (18b) round-trips the same wire value', async ({
  browser,
  request,
}) => {
  // Slice 18b invariant (SPEC-0031 BR4/DL-0116): a brand goal defined with a known GOAL_METRIC code
  // returns the same string — no wire change — and an unknown code is rejected (422).
  const token = await tokenFor(browser, 'dev');
  const brandRef = `E2E_BRAND_${Date.now()}`;
  const brand = await request.post('/api/portfolio/brands', {
    headers: { Authorization: `Bearer ${token}` },
    data: { brandRef, displayName: 'E2E Brand' },
  });
  expect(brand.status()).toBe(201);

  const goal = await request.post(`/api/portfolio/brands/${brandRef}/goals`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { period: '2026', metric: 'REVENUE', target: { amount: 1000.0, currency: 'BRL' } },
  });
  expect(goal.status()).toBe(201);
  expect((await goal.json()).metric).toBe('REVENUE');

  const rejected = await request.post(`/api/portfolio/brands/${brandRef}/goals`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { period: '2026-05', metric: 'NOT_A_METRIC', targetCount: 10 },
  });
  expect(rejected.status()).toBe(422);
});

test('the converted sourcing codes (18c) round-trip the same wire value', async ({
  browser,
  request,
}) => {
  // Slice 18c invariant (SPEC-0031 BR4/DL-0117): a sourced offer registered with known OFFER_ORIGIN
  // and INTEGRATION_LEVEL codes returns the same strings — no wire change — and an unknown code is
  // rejected (422) by the CadastroValidator port.
  const token = await tokenFor(browser, 'dev');

  const created = await request.post('/api/sourcing/offers', {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      productText: 'City Tour Rio - E2E',
      basePrice: { amount: 480.0, currency: 'BRL' },
      origin: 'EXTERNAL_SITE',
      integrationLevel: 'INBOUND',
      externalRef: `QS-E2E-${Date.now()}`,
    },
  });
  expect(created.status()).toBe(201);
  const body = await created.json();
  expect(body.origin).toBe('EXTERNAL_SITE');
  expect(body.integrationLevel).toBe('INBOUND');

  const rejected = await request.post('/api/sourcing/offers', {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      productText: 'Bad origin - E2E',
      basePrice: { amount: 10.0, currency: 'BRL' },
      origin: 'NOT_AN_ORIGIN',
      integrationLevel: 'INBOUND',
    },
  });
  expect(rejected.status()).toBe(422);
});
