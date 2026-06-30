import { Component, inject, model } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { DialogModule } from 'primeng/dialog';
import { CommandRegistry } from '../../core/commands/command-registry.service';

/**
 * Keyboard-help dialog (SPEC-0026 BR5, DL-0093): lists the shortcuts derived from the same
 * {@link CommandRegistry} the palette uses, so help and behaviour never drift. Opened by `?`.
 */
@Component({
  selector: 'app-keyboard-help',
  imports: [DialogModule, TranslatePipe],
  templateUrl: './keyboard-help.html',
})
export class KeyboardHelp {
  private readonly registry = inject(CommandRegistry);

  /** Two-way open state (bound by the shell to the shortcut service). */
  readonly open = model(false);

  /** Commands that advertise a shortcut hint. */
  readonly shortcuts = this.registry.shortcuts;
}
