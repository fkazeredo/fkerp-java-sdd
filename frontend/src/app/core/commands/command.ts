/** A command that can be run from the palette (`Ctrl/Cmd+K`) or via a keyboard shortcut (DL-0093). */
export interface Command {
  /** Stable id. */
  readonly id: string;
  /** i18n key for the visible label. */
  readonly labelKey: string;
  /** PrimeIcons class (e.g. `pi pi-home`). */
  readonly icon?: string;
  /** Human-readable shortcut hint shown in the help dialog (e.g. `g a`); display-only. */
  readonly hint?: string;
  /** A coarse grouping i18n key (e.g. `command.group.navigation`) for the palette/help. */
  readonly groupKey?: string;
  /** Executes the command. */
  readonly run: () => void;
}
