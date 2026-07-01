import { DOCUMENT, Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NAV_ITEMS } from '../layout/nav';

/**
 * Global keyboard shortcuts (SPEC-0026 BR4/BR5, DL-0093). A single document listener:
 * - `Ctrl/Cmd+K` opens the command palette (works everywhere, even in inputs);
 * - `?` (Shift+/) opens the keyboard-help dialog;
 * - a leader `g` followed by a nav key (e.g. `g a` → Accounts) navigates.
 *
 * Single-letter shortcuts are ignored while the focus is in an editable field, so typing in forms is
 * never hijacked. The shell mounts/unmounts the listener and binds the open signals to the dialogs.
 */
@Injectable({ providedIn: 'root' })
export class ShortcutService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);

  /** Whether the command palette is open. */
  readonly paletteOpen = signal(false);
  /** Whether the keyboard-help dialog is open. */
  readonly helpOpen = signal(false);

  private leaderActive = false;
  private leaderTimer: ReturnType<typeof setTimeout> | null = null;
  private listener: ((event: KeyboardEvent) => void) | null = null;

  /**
   * Maps the second key of a `g`-prefixed shortcut to a nav path (first letter of each path). When
   * two nav paths share a first letter (e.g. accounts/aftersales), the first-registered item keeps
   * the letter; the later one stays reachable through the command palette (SPEC-0026 BR4).
   */
  private readonly navByKey = NAV_ITEMS.reduce((map, item) => {
    const key = item.path[0].toLowerCase();
    if (!map.has(key)) {
      map.set(key, item.path);
    }
    return map;
  }, new Map<string, string>());

  /** Starts listening for global shortcuts (called by the shell). Idempotent. */
  start(): void {
    if (this.listener) {
      return;
    }
    this.listener = (event) => this.handle(event);
    this.document.addEventListener('keydown', this.listener);
  }

  /** Stops listening (called when the shell is destroyed). */
  stop(): void {
    if (this.listener) {
      this.document.removeEventListener('keydown', this.listener);
      this.listener = null;
    }
    this.clearLeader();
  }

  /** Opens the command palette. */
  openPalette(): void {
    this.paletteOpen.set(true);
  }

  /** Whether the given target is an editable field where letter shortcuts must be ignored. */
  private isEditable(target: EventTarget | null): boolean {
    const el = target as HTMLElement | null;
    if (!el) {
      return false;
    }
    const tag = el.tagName;
    return (
      tag === 'INPUT' ||
      tag === 'TEXTAREA' ||
      tag === 'SELECT' ||
      el.isContentEditable === true
    );
  }

  private handle(event: KeyboardEvent): void {
    // Ctrl/Cmd+K — always opens the palette (even from an input).
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.paletteOpen.set(true);
      this.clearLeader();
      return;
    }

    // Letter shortcuts are ignored while typing in a field or with modifier keys held.
    if (this.isEditable(event.target) || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }

    // `?` opens the help dialog.
    if (event.key === '?') {
      event.preventDefault();
      this.helpOpen.set(true);
      this.clearLeader();
      return;
    }

    // Leader `g` then a nav key.
    if (this.leaderActive) {
      const path = this.navByKey.get(event.key.toLowerCase());
      this.clearLeader();
      if (path) {
        event.preventDefault();
        void this.router.navigate(['/' + path]);
      }
      return;
    }
    if (event.key.toLowerCase() === 'g') {
      this.leaderActive = true;
      this.leaderTimer = setTimeout(() => this.clearLeader(), 1200);
    }
  }

  private clearLeader(): void {
    this.leaderActive = false;
    if (this.leaderTimer) {
      clearTimeout(this.leaderTimer);
      this.leaderTimer = null;
    }
  }
}
