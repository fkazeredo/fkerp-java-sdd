import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Login screen (SPEC-0024 — graduated to OIDC in Phase 13, DL-0106). Login is delegated to the
 * external IdP (Keycloak): the "Entrar com SSO" button starts the Authorization Code + PKCE flow,
 * which redirects to the IdP's hosted login page; on return the app completes the session
 * (app.config bootstrap) and routes to the intended page (honouring `returnUrl`). The ERP no longer
 * collects the password — credentials are handled by the IdP (better security posture).
 */
@Component({
  selector: 'app-login-page',
  imports: [TranslatePipe, ButtonModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);

  /** Starts the OIDC login, preserving the intended destination as the post-login return URL. */
  signIn(): void {
    this.submitting.set(true);
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
    this.auth.login(returnUrl);
  }
}
