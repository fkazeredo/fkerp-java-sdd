import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiError } from '../../core/http/api-error';
import { BookingStatus, BookingView, LocatorOrigin } from './booking.models';
import { BookingService } from './booking.service';

const ALLOWED: Record<BookingStatus, string[]> = {
  QUOTED: ['order'],
  ORDERED: ['pending'],
  PENDING: ['confirm', 'cancel'],
  CONFIRMED: ['change', 'no-show', 'complete', 'cancel'],
  CHANGED: ['confirm', 'cancel'],
  CANCELLED: [],
  NO_SHOW: [],
  COMPLETED: [],
};

/**
 * Booking screen (SPEC-0006): creates a booking from a quote and shows its current state with only
 * the lifecycle actions allowed by the state machine; cancellation asks for a reason.
 */
@Component({
  selector: 'app-booking-page',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './booking-page.html',
})
export class BookingPage {
  private readonly bookingService = inject(BookingService);

  readonly booking = signal<BookingView | null>(null);
  readonly createError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly busy = signal(false);

  readonly allowedActions = computed(() => {
    const current = this.booking();
    return current ? ALLOWED[current.status] : [];
  });

  quoteId = '';
  origin: LocatorOrigin = 'EXTERNAL';
  code = '';
  cancelReason = '';

  /** Creates the booking and shows its detail. */
  create(): void {
    this.busy.set(true);
    this.createError.set(null);
    this.bookingService
      .create({
        quoteId: this.quoteId,
        locator: { origin: this.origin, code: this.origin === 'EXTERNAL' ? this.code : null },
      })
      .subscribe({
        next: (booking) => {
          this.busy.set(false);
          this.booking.set(booking);
        },
        error: (error: ApiError) => {
          this.busy.set(false);
          this.createError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Translation key for a lifecycle action button (flat, e.g. {@code booking.action_no_show}). */
  actionKey(action: string): string {
    return 'booking.action_' + action.replace('-', '_');
  }

  /** Applies a no-body lifecycle action. */
  act(action: string): void {
    const current = this.booking();
    if (!current) {
      return;
    }
    this.run(this.bookingService.transition(current.id, action));
  }

  /** Cancels with the typed reason. */
  cancel(): void {
    const current = this.booking();
    if (!current) {
      return;
    }
    this.run(this.bookingService.cancel(current.id, this.cancelReason));
  }

  private run(call: ReturnType<BookingService['transition']>): void {
    this.busy.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: (booking) => {
        this.busy.set(false);
        this.booking.set(booking);
        this.cancelReason = '';
      },
      error: (error: ApiError) => {
        this.busy.set(false);
        this.actionError.set(error?.code ?? 'error.internal');
      },
    });
  }
}
