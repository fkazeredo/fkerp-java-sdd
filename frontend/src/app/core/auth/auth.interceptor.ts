import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the OIDC bearer access token to outgoing API calls (SPEC-0024 Phase 13) so the backend
 * can resolve the user and enforce roles. On a 401 the local session is cleared (without an IdP
 * round-trip) and the user is sent to the login screen — the backend stays the authority; the
 * frontend only reacts to its verdict.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.token();
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      const status = (error as { status?: number })?.status;
      if (status === 401) {
        auth.clearLocalSession();
        const current = router.url.split('?')[0];
        const onLogin = current === '/login' || current === '/';
        void router.navigate(['/login'], onLogin ? {} : { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
