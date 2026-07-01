import { test, expect } from '@playwright/test';
import { tokenFor } from './helpers';

/**
 * Permission sad path (SPEC-0028 AC4 family / SPEC-0024 DL-0082; self-hosted OIDC tokens — Phase 17).
 * The backend is the single authorization authority: a sensitive action requires the corresponding
 * role, and a token without it is denied with 403 — proven end-to-end against the AS-issued token.
 *
 * `viewer` (ROLE_VIEWER) and `director` (ROLE_DIRECTOR) are local seed users (DL-0112). The token is
 * obtained through the genuine Authorization Code + PKCE browser flow (the self-hosted AS, being
 * OAuth 2.1, offers no direct-grant), then replayed on the API. Issuing a commercial-policy directive
 * requires ROLE_DIRECTOR (SecurityConfig).
 */

test('a viewer is denied (403) when issuing a director-only directive', async ({
  browser,
  request,
}) => {
  const token = await tokenFor(browser, 'viewer');
  const res = await request.post('/api/commercial-policy/directives', {
    headers: { Authorization: `Bearer ${token}` },
    data: { key: 'MARKUP_PCT', value: '5', type: 'PERCENT', justification: 'e2e attempt' },
  });
  expect(res.status()).toBe(403);
});

test('an unauthenticated call to a protected endpoint is rejected (401)', async ({ request }) => {
  // No Authorization header → the resource server rejects with 401 (generic — SPEC-0024 BR4).
  const res = await request.get('/api/accounts');
  expect(res.status()).toBe(401);
});

test('a director is allowed past the authorization gate for the same directive', async ({
  browser,
  request,
}) => {
  // The same call with the right role must NOT be 401/403 — proves the gate is role-specific, not a
  // blanket block. (The request body may still be rejected on business grounds, but never on authz.)
  const token = await tokenFor(browser, 'director');
  const res = await request.post('/api/commercial-policy/directives', {
    headers: { Authorization: `Bearer ${token}` },
    data: { key: 'MARKUP_PCT', value: '5', type: 'PERCENT', justification: 'e2e director check' },
  });
  expect(res.status()).not.toBe(401);
  expect(res.status()).not.toBe(403);
});
