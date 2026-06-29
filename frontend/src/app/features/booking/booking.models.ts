export type LocatorOrigin = 'INTERNAL' | 'EXTERNAL';
export type BookingStatus =
  | 'QUOTED'
  | 'ORDERED'
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHANGED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'COMPLETED';

/** Booking resource returned by the backend (SPEC-0006). */
export interface BookingView {
  id: string;
  quoteId: string;
  accountId: string;
  status: BookingStatus;
  locator: { origin: LocatorOrigin; code: string };
  pendingSince: string | null;
  confirmedAt: string | null;
  cancelReason: string | null;
  createdAt: string;
}

/** Body for `POST /api/bookings`. */
export interface CreateBookingRequest {
  quoteId: string;
  locator: { origin: LocatorOrigin; code?: string | null };
}
