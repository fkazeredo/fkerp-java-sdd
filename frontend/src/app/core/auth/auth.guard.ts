import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Route guard (SPEC-0024/SPEC-0026 BR7): allows navigation only when a user is authenticated,
 * otherwise redirects to the login screen preserving the attempted URL as `returnUrl` so the user
 * lands back where they intended after signing in. This is a UX convenience — the real authorization
 * is enforced by the backend on every API call; the guard never grants access by itself.
 */
export const authGuard: CanActivateFn = (
  _route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
