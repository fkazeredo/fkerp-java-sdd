import { HttpInterceptorFn } from '@angular/common/http';

/** Header name shared with the backend CorrelationIdFilter. */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

function newCorrelationId(): string {
  const cryptoObj = globalThis.crypto;
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID();
  }
  return `cid-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * Tags backend-bound requests with an `X-Correlation-Id` so a request can be traced end to end across
 * frontend and backend logs (observability.md). The backend echoes/propagates the same id.
 *
 * <p>It is applied only to the app's own API (same-origin / `/api`) — NOT to cross-origin calls such
 * as the OIDC IdP's discovery/token endpoints, whose CORS policy would reject an unexpected header
 * (Phase 13/DL-0106). Those requests are left untouched.
 */
export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (isCrossOrigin(req.url)) {
    return next(req);
  }
  const withCorrelationId = req.clone({
    setHeaders: { [CORRELATION_ID_HEADER]: newCorrelationId() },
  });
  return next(withCorrelationId);
};

/** Whether the URL is an absolute cross-origin URL (a different origin than the app). */
function isCrossOrigin(url: string): boolean {
  if (!/^https?:\/\//i.test(url)) {
    return false; // relative URL → same origin (the backend via /api)
  }
  try {
    return new URL(url).origin !== globalThis.location.origin;
  } catch {
    return false;
  }
}
