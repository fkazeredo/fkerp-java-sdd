import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';

function normalize(error: HttpErrorResponse): ApiError {
  const body = error.error as Partial<ApiError> | null;
  if (body && typeof body.code === 'string') {
    return { code: body.code, message: body.message ?? '', fields: body.fields ?? [] };
  }
  if (error.status === 0) {
    return { code: 'error.network', message: 'Network error', fields: [] };
  }
  return { code: 'error.internal', message: error.message, fields: [] };
}

/**
 * Normalizes any failed HTTP response into the stable {@link ApiError} contract `{code, message,
 * fields}` (matching the backend, ADR 0011), so feature code reacts to a stable error `code` rather
 * than to raw transport errors. Global normalization here; feature-specific presentation stays in
 * the features (frontend-angular.md).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(catchError((error: HttpErrorResponse) => throwError(() => normalize(error))));
