import { TestBed } from '@angular/core/testing';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { BookingPage } from './booking-page';
import { BookingView } from './booking.models';
import { BookingService } from './booking.service';

const BOOKING: BookingView = {
  id: 'b1',
  quoteId: 'q1',
  accountId: 'a1',
  status: 'ORDERED',
  locator: { origin: 'EXTERNAL', code: 'ALAMO-1' },
  pendingSince: null,
  confirmedAt: null,
  cancelReason: null,
  createdAt: '2026-06-29T00:00:00Z',
};

function configure(service: Partial<BookingService>): void {
  TestBed.configureTestingModule({
    imports: [BookingPage],
    providers: [
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: BookingService, useValue: service },
    ],
  });
}

describe('BookingPage', () => {
  it('creates a booking and exposes only the allowed action for its state', () => {
    configure({ create: () => of(BOOKING) });
    const fixture = TestBed.createComponent(BookingPage);
    fixture.detectChanges();

    fixture.componentInstance.create();

    expect(fixture.componentInstance.booking()?.status).toBe('ORDERED');
    expect(fixture.componentInstance.allowedActions()).toEqual(['pending']);
  });

  it('surfaces the quote-not-found error', () => {
    configure({
      create: () => throwError(() => ({ code: 'booking.quote.not-found', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(BookingPage);
    fixture.detectChanges();

    fixture.componentInstance.create();

    expect(fixture.componentInstance.createError()).toBe('booking.quote.not-found');
  });
});
