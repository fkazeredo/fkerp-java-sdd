import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/http/api-error';

/**
 * Login screen (SPEC-0024): collects username/password, calls the backend, stores the session and
 * navigates to the app. Shows a submitting state and a generic error (the backend never reveals
 * whether the user exists — BR4). The dev seed users (e.g. director/finance/it, password dev12345)
 * exist only under the dev profile.
 */
@Component({
  selector: 'app-login-page',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

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
        void this.router.navigate(['/accounts']);
      },
      error: (error: ApiError) => {
        this.submitting.set(false);
        this.errorCode.set(error?.code ?? 'auth.unauthenticated');
      },
    });
  }
}
