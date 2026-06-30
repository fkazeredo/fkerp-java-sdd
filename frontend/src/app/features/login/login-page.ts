import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/http/api-error';

/**
 * Login screen (SPEC-0024 / SPEC-0026 BR6): collects username/password, calls the backend, stores the
 * session and navigates to the app (honouring `returnUrl` when present). Shows a submitting state and
 * a generic error (the backend never reveals whether the user exists — SPEC-0024 BR4). Repaginated
 * with PrimeNG. The dev seed users (e.g. director/finance/it, password dev12345) exist only under the
 * dev profile.
 */
@Component({
  selector: 'app-login-page',
  imports: [FormsModule, TranslatePipe, ButtonModule, InputTextModule, PasswordModule, MessageModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly errorCode = signal<string | null>(null);

  username = '';
  password = '';

  submit(): void {
    if (!this.username || !this.password) {
      return;
    }
    this.submitting.set(true);
    this.errorCode.set(null);
    this.auth.login({ username: this.username, password: this.password }).subscribe({
      next: () => {
        this.submitting.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        void this.router.navigateByUrl(returnUrl);
      },
      error: (error: ApiError) => {
        this.submitting.set(false);
        this.errorCode.set(error?.code ?? 'auth.unauthenticated');
      },
    });
  }
}
