import {
  Component,
  computed,
  effect,
  inject,
  model,
  signal,
  viewChild,
  ElementRef,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DialogModule } from 'primeng/dialog';
import { Command } from '../../core/commands/command';
import { CommandRegistry } from '../../core/commands/command-registry.service';

/**
 * Command palette (SPEC-0026 BR4, DL-0093): a dialog with a filter box and a keyboard-navigable list
 * of commands from the {@link CommandRegistry}. `Ctrl/Cmd+K` opens it (wired by the shell), the input
 * autofocuses, ↑/↓ move the selection, Enter runs it, Esc closes. PrimeNG Dialog provides the modal
 * with focus trapping for accessibility.
 */
@Component({
  selector: 'app-command-palette',
  imports: [FormsModule, DialogModule, TranslatePipe],
  templateUrl: './command-palette.html',
})
export class CommandPalette {
  private readonly registry = inject(CommandRegistry);
  private readonly translate = inject(TranslateService);

  /** Two-way open state (bound by the shell to the shortcut service). */
  readonly open = model(false);

  private readonly searchInput =
    viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly query = signal('');
  readonly activeIndex = signal(0);

  /** Commands filtered by the (translated) label against the query. */
  readonly filtered = computed<Command[]>(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.registry.commands();
    if (!q) {
      return [...all];
    }
    return all.filter((c) => this.translate.instant(c.labelKey).toLowerCase().includes(q));
  });

  constructor() {
    // Reset and focus when the palette opens.
    effect(() => {
      if (this.open()) {
        this.query.set('');
        this.activeIndex.set(0);
        queueMicrotask(() => this.searchInput()?.nativeElement.focus());
      }
    });
  }

  onQueryChange(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
  }

  onKeydown(event: KeyboardEvent): void {
    const items = this.filtered();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex.update((i) => Math.min(i + 1, items.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex.update((i) => Math.max(i - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.runActive();
    }
  }

  /** Runs the currently highlighted command and closes the palette. */
  runActive(): void {
    const item = this.filtered()[this.activeIndex()];
    if (item) {
      this.close();
      item.run();
    }
  }

  /** Runs a specific command (mouse click) and closes. */
  runCommand(command: Command): void {
    this.close();
    command.run();
  }

  close(): void {
    this.open.set(false);
  }
}
