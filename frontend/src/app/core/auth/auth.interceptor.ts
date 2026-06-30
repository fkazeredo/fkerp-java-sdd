import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the bearer token to outgoing API calls (SPEC-0024) so the backend can resolve the user
 * and enforce roles. The login endpoint is left untouched (it has no token yet). On a 401 the local
 * session is cleared and the user is sent to the login screen — the backend stays the authority; the
 * frontend only reacts to its verdict.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.token();
  const isLogin = req.url.endsWith('/identity/login');
  const request =
    token && !isLogin
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      const status = (error as { status?: number })?.status;
      if (status === 401 && !isLogin) {
        auth.logout();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
