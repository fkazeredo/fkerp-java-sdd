/**
 * Base path for the backend API. Relative so the dev proxy (proxy.conf.json) and the production
 * reverse proxy both route `/api/*` to the backend without rebuilds.
 */
export const API_BASE_URL = '/api';
