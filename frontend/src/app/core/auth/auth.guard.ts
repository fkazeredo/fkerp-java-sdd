import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Route guard (SPEC-0024): allows navigation only when a user is authenticated, otherwise redirects
 * to the login screen. This is a UX convenience — the real authorization is enforced by the backend
 * on every API call; the guard never grants access by itself.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};
