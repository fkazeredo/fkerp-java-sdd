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
 * Tags every outgoing request with an `X-Correlation-Id` so a request can be traced end to end
 * across frontend and backend logs (observability.md). The backend echoes/propagates the same id.
 */
export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  const withCorrelationId = req.clone({
    setHeaders: { [CORRELATION_ID_HEADER]: newCorrelationId() },
  });
  return next(withCorrelationId);
};
