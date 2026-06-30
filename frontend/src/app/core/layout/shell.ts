import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../auth/auth.service';
import { ThemeService } from '../theme/theme.service';
import { NAV_ITEMS, NavItem } from './nav';

/**
 * SaaS application shell (SPEC-0026 BR2): a workflow-oriented sidebar, a top bar (brand, theme
 * toggle, current user / sign-out) and a responsive drawer for narrow viewports. The routed screen
 * renders in the content area. Navigation reflects the user's roles for tidiness only — the backend
 * stays the authorization authority (security.md).
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, ButtonModule, TooltipModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  /** The current user (null when logged out), mirrored from the verified token (SPEC-0024). */
  readonly user = this.auth.user;
  /** Whether the dark theme is active. */
  readonly isDark = this.theme.isDark;
  /** Whether the mobile drawer is open. */
  readonly drawerOpen = signal(false);

  /** Navigation items visible to the current user (role-filtered — UX only, BR2). */
  readonly navItems = computed<NavItem[]>(() => {
    const roles = this.user()?.roles ?? [];
    return NAV_ITEMS.filter(
      (item) => !item.roles || item.roles.some((role) => roles.includes(role)),
    );
  });

  /** Toggles light/dark theme. */
  toggleTheme(): void {
    this.theme.toggle();
  }

  /** Opens/closes the mobile navigation drawer. */
  toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
  }

  /** Closes the mobile drawer (e.g. after navigating). */
  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  /** Clears the session and returns to the login screen. */
  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
