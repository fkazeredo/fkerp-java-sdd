import { Component, computed, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

/** The states a data-backed screen section can be in (SPEC-0026 BR8). */
export type ScreenStateKind = 'loading' | 'success' | 'error' | 'empty';

/** Backend error codes that mean "permission denied" (403) — shown as a permission state, not error. */
const PERMISSION_CODES = new Set(['access.denied', 'error.forbidden']);

/**
 * Presentational helper for the real usage states required on every screen (SPEC-0026 BR8, AC9):
 * loading, empty, error (with retry) and permission-denied. The host passes the current state and,
 * for errors, the backend error code; a 403-style code is rendered as a permission message instead
 * of a generic error. The success content is projected as the component's children.
 */
@Component({
  selector: 'app-screen-state',
  imports: [TranslatePipe, ButtonModule, MessageModule, ProgressSpinnerModule],
  template: `
    @switch (state()) {
      @case ('loading') {
        <div class="screen-state screen-state--loading" data-testid="state-loading">
          <p-progress-spinner
            styleClass="screen-state__spinner"
            [attr.aria-label]="'common.loading' | translate"
          />
          <p class="app-muted">{{ 'common.loading' | translate }}</p>
        </div>
      }
      @case ('error') {
        @if (isPermission()) {
          <p-message severity="warn" styleClass="screen-state__msg" data-testid="state-permission">
            {{ 'common.permissionDenied' | translate }}
          </p-message>
        } @else {
          <div class="screen-state screen-state--error" data-testid="state-error">
            <p-message severity="error" styleClass="screen-state__msg">
              {{ (errorCode() ?? 'error.internal') | translate }}
            </p-message>
            <p-button
              size="small"
              [label]="'common.retry' | translate"
              icon="pi pi-refresh"
              (onClick)="retry.emit()"
              data-testid="state-retry"
            />
          </div>
        }
      }
      @case ('empty') {
        <p class="app-muted" data-testid="state-empty">
          {{ emptyKey() | translate }}
        </p>
      }
      @default {
        <ng-content />
      }
    }
  `,
  styles: [
    `
      .screen-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.5rem;
        padding: 1rem 0;
      }
      .screen-state--error {
        align-items: flex-start;
      }
      :host ::ng-deep .screen-state__spinner {
        width: 2.5rem;
        height: 2.5rem;
      }
      :host ::ng-deep .screen-state__msg {
        display: block;
        margin: 0;
      }
    `,
  ],
})
export class ScreenState {
  /** Current state of the section. `success` projects the children. */
  readonly state = input.required<ScreenStateKind>();
  /** Backend error code (shown when state is `error` and not a permission code). */
  readonly errorCode = input<string | null>(null);
  /** i18n key for the empty message. */
  readonly emptyKey = input<string>('common.empty');
  /** Emitted when the user clicks retry. */
  readonly retry = output<void>();

  /** Whether the current error code denotes a permission denial (403). */
  readonly isPermission = computed(() => PERMISSION_CODES.has(this.errorCode() ?? ''));
}
