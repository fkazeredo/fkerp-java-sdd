import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { AccountsService } from '../accounts/accounts.service';
import { BookingService } from '../booking/booking.service';
import { ExchangeService } from '../exchange/exchange.service';
import { ReconciliationService } from '../reconciliation/reconciliation.service';
import { Money } from '../../core/models/api.models';

/** Accounts KPI: total and a breakdown by status. */
export interface AccountsKpi {
  readonly total: number;
  readonly active: number;
}

/** Bookings KPI: total and the operationally interesting counts. */
export interface BookingsKpi {
  readonly total: number;
  readonly pending: number;
  readonly confirmed: number;
}

/** Reconciliation KPI: case counts and the expected-spread sum. */
export interface ReconciliationKpi {
  readonly total: number;
  readonly open: number;
  readonly discrepancy: number;
  readonly expectedSpread: Money | null;
}

/** Exchange KPI: the prevailing pinned rate for the default pair. */
export interface ExchangeKpi {
  readonly pair: string;
  readonly rate: number;
}

/**
 * Dashboard read service (SPEC-0026 BR10, DL-0094). It composes the KPIs on the client from the
 * feature services' existing list endpoints — no new backend endpoint. Each KPI is a separate call
 * so the dashboard can show an independent loading/empty/error state per card.
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly accounts = inject(AccountsService);
  private readonly bookings = inject(BookingService);
  private readonly reconciliation = inject(ReconciliationService);
  private readonly exchange = inject(ExchangeService);

  /** Default currency pair for the exchange KPI. */
  static readonly DEFAULT_PAIR = 'USD/BRL';

  accountsKpi(): Observable<AccountsKpi> {
    return this.accounts.list().pipe(
      map((page) => ({
        total: page.totalElements,
        active: page.content.filter((a) => a.status === 'ACTIVE').length,
      })),
    );
  }

  bookingsKpi(): Observable<BookingsKpi> {
    return this.bookings.list().pipe(
      map((page) => ({
        total: page.totalElements,
        pending: page.content.filter((b) => b.status === 'PENDING').length,
        confirmed: page.content.filter((b) => b.status === 'CONFIRMED').length,
      })),
    );
  }

  reconciliationKpi(): Observable<ReconciliationKpi> {
    return this.reconciliation.list().pipe(
      map((page) => {
        const cases = page.content;
        const expected = cases.reduce((sum, c) => sum + (c.expectedSpread?.amount ?? 0), 0);
        const currency = cases[0]?.expectedSpread?.currency ?? 'BRL';
        return {
          total: page.totalElements,
          open: cases.filter((c) => c.status === 'OPEN').length,
          discrepancy: cases.filter((c) => c.status === 'DISCREPANCY').length,
          expectedSpread: cases.length ? { amount: expected, currency } : null,
        };
      }),
    );
  }

  exchangeKpi(pair = DashboardService.DEFAULT_PAIR): Observable<ExchangeKpi> {
    return this.exchange
      .current(pair)
      .pipe(map((rate) => ({ pair: rate.currencyPair, rate: rate.rate })));
  }
}
