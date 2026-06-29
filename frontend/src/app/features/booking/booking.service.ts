import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { BookingView, CreateBookingRequest } from './booking.models';

/** Feature API service for bookings (SPEC-0006). */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);

  /** Creates a booking from a quote. */
  create(request: CreateBookingRequest): Observable<BookingView> {
    return this.http.post<BookingView>(`${API_BASE_URL}/bookings`, request);
  }

  /** Applies a no-body lifecycle action (pending/confirm/no-show/complete/change). */
  transition(id: string, action: string): Observable<BookingView> {
    return this.http.post<BookingView>(`${API_BASE_URL}/bookings/${id}/${action}`, null);
  }

  /** Cancels a booking with a reason. */
  cancel(id: string, reason: string): Observable<BookingView> {
    return this.http.post<BookingView>(`${API_BASE_URL}/bookings/${id}/cancel`, { reason });
  }
}
