import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

/**
 * A page that may hold unsaved work (SPEC-0026 BR9). When `isDirty()` is true the guard asks for
 * confirmation before leaving; cancelling keeps the user on the page.
 */
export interface FormLeaveGuard {
  /** Whether the form has unsaved changes. */
  isDirty(): boolean;
}

/**
 * `canDeactivate` guard (SPEC-0026 BR9, AC8): blocks navigation away from a dirty form until the
 * user confirms. Uses the platform `confirm()` so it works without extra wiring; the message comes
 * from i18n. Components opt in by implementing {@link FormLeaveGuard}.
 */
export const canDeactivateGuard: CanDeactivateFn<FormLeaveGuard> = (component) => {
  if (!component?.isDirty?.()) {
    return true;
  }
  const translate = inject(TranslateService);
  return confirm(translate.instant('common.unsavedConfirm'));
};
