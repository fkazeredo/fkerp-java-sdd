import { Injectable, computed, signal } from '@angular/core';
import { Command } from './command';

/**
 * Central registry of runnable commands (DL-0093). The command palette and the keyboard-help dialog
 * both read from here, so they stay consistent. Features can register contextual commands and remove
 * them when they leave. The registry holds no UI — it is the single source of truth for "what can be
 * done from the keyboard".
 */
@Injectable({ providedIn: 'root' })
export class CommandRegistry {
  private readonly commandsSignal = signal<readonly Command[]>([]);

  /** All registered commands, in registration order. */
  readonly commands = this.commandsSignal.asReadonly();

  /** Commands that have a shortcut hint (for the help dialog). */
  readonly shortcuts = computed(() => this.commandsSignal().filter((c) => !!c.hint));

  /** Registers commands; returns a disposer that removes exactly those commands. */
  register(commands: readonly Command[]): () => void {
    this.commandsSignal.update((current) => [...current, ...commands]);
    const ids = new Set(commands.map((c) => c.id));
    return () => {
      this.commandsSignal.update((current) => current.filter((c) => !ids.has(c.id)));
    };
  }

  /** Runs the command with the given id, if present. */
  run(id: string): void {
    this.commandsSignal()
      .find((c) => c.id === id)
      ?.run();
  }
}
