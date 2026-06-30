import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslateService, TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../auth/auth.service';
import { Command } from '../commands/command';
import { CommandRegistry } from '../commands/command-registry.service';
import { ShortcutService } from '../commands/shortcut.service';
import { ThemeService } from '../theme/theme.service';
import { CommandPalette } from '../../shared/command-palette/command-palette';
import { KeyboardHelp } from '../../shared/keyboard-help/keyboard-help';
import { NAV_ITEMS, NavItem } from './nav';

/**
 * SaaS application shell (SPEC-0026 BR2): a workflow-oriented sidebar, a top bar (brand, command
 * palette, theme toggle, current user / sign-out) and a responsive drawer for narrow viewports. It
 * also mounts the global keyboard shortcuts and registers the base commands (navigation, theme,
 * session) into the {@link CommandRegistry}. Navigation reflects the user's roles for tidiness only —
 * the backend stays the authorization authority (security.md).
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslatePipe,
    ButtonModule,
    TooltipModule,
    CommandPalette,
    KeyboardHelp,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly registry = inject(CommandRegistry);
  private readonly translate = inject(TranslateService);
  protected readonly shortcuts = inject(ShortcutService);

  /** The current user (null when logged out), mirrored from the verified token (SPEC-0024). */
  readonly user = this.auth.user;
  /** Whether the dark theme is active. */
  readonly isDark = this.theme.isDark;
  /** Whether the mobile drawer is open. */
  readonly drawerOpen = signal(false);

  private readonly disposeCommands: () => void;

  /** Navigation items visible to the current user (role-filtered — UX only, BR2). */
  readonly navItems = computed<NavItem[]>(() => {
    const roles = this.user()?.roles ?? [];
    return NAV_ITEMS.filter(
      (item) => !item.roles || item.roles.some((role) => roles.includes(role)),
    );
  });

  constructor() {
    this.shortcuts.start();
    this.disposeCommands = this.registry.register(this.buildCommands());
  }

  ngOnDestroy(): void {
    this.shortcuts.stop();
    this.disposeCommands();
  }

  /** Opens the command palette (topbar button). */
  openPalette(): void {
    this.shortcuts.openPalette();
  }

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

  /** The base command set: navigate to each screen, toggle theme, sign out (DL-0093). */
  private buildCommands(): Command[] {
    const navCommands: Command[] = NAV_ITEMS.map((item) => ({
      id: `nav.${item.path}`,
      labelKey: item.labelKey,
      icon: item.icon,
      hint: `g ${item.path[0]}`,
      groupKey: 'command.group.navigation',
      run: () => {
        this.closeDrawer();
        void this.router.navigate(['/' + item.path]);
      },
    }));
    return [
      ...navCommands,
      {
        id: 'theme.toggle',
        labelKey: 'shell.toggleTheme',
        icon: 'pi pi-moon',
        groupKey: 'command.group.app',
        run: () => this.toggleTheme(),
      },
      {
        id: 'session.logout',
        labelKey: 'nav.logout',
        icon: 'pi pi-sign-out',
        groupKey: 'command.group.app',
        run: () => this.logout(),
      },
    ];
  }
}
