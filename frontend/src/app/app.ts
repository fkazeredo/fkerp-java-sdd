import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** The current user (null when logged out), mirrored from the verified token (SPEC-0024). */
  readonly user = this.auth.user;

  /** Clears the session and returns to the login screen. */
  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
