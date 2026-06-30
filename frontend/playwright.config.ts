import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests (SPEC-0028 / DL-0102). They run against an isolated, throwaway stack with an
 * EPHEMERAL database so they never touch the development data: `npm run e2e:up` (brings up
 * compose.e2e.yaml — frontend on port 4201, backend 8081, ephemeral Postgres), then `npm run e2e`,
 * then `npm run e2e:down`. Override the target with E2E_BASE_URL (e.g. to point at the dev stack on
 * 4200). Headless by default; chromium only (multi-browser is out of scope for this phase).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // No accidental .only slipping through CI and masking the rest of the suite.
  forbidOnly: !!process.env.CI,
  // One retry everywhere: the journeys drive many sequential UI steps against a single shared backend,
  // so under parallel load a dropdown/toast can occasionally miss the timeout. The retry absorbs that
  // infra timing without weakening any assertion (the assertion is identical on the retry).
  retries: 1,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4201',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
