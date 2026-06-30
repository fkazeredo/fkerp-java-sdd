import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AccountsService } from '../accounts/accounts.service';
import { BookingService } from '../booking/booking.service';
import { ExchangeService } from '../exchange/exchange.service';
import { ReconciliationService } from '../reconciliation/reconciliation.service';
import { DashboardService } from './dashboard.service';

function page<T>(content: T[], total = content.length) {
  return { content, page: 0, size: 20, totalElements: total, totalPages: 1 };
}

describe('DashboardService (DL-0094 — client-side KPIs)', () => {
  function configure(
    accounts: Partial<AccountsService>,
    bookings: Partial<BookingService>,
    reconciliation: Partial<ReconciliationService>,
    exchange: Partial<ExchangeService>,
  ): DashboardService {
    TestBed.configureTestingModule({
      providers: [
        DashboardService,
        { provide: AccountsService, useValue: accounts },
        { provide: BookingService, useValue: bookings },
        { provide: ReconciliationService, useValue: reconciliation },
        { provide: ExchangeService, useValue: exchange },
      ],
    });
    return TestBed.inject(DashboardService);
  }

  it('derives the accounts KPI (total + active) from the list', () => {
    const service = configure(
      {
        list: () =>
          of(
            page(
              [
                { status: 'ACTIVE' } as never,
                { status: 'ACTIVE' } as never,
                { status: 'SUSPENDED' } as never,
              ],
              3,
            ),
          ),
      },
      {},
      {},
      {},
    );

    let result: { total: number; active: number } | undefined;
    service.accountsKpi().subscribe((r) => (result = r));
    expect(result).toEqual({ total: 3, active: 2 });
  });

  it('derives the bookings KPI (pending/confirmed) from the list', () => {
    const service = configure(
      {},
      {
        list: () =>
          of(
            page([
              { status: 'PENDING' } as never,
              { status: 'CONFIRMED' } as never,
              { status: 'CONFIRMED' } as never,
            ]),
          ),
      },
      {},
      {},
    );

    let result: { total: number; pending: number; confirmed: number } | undefined;
    service.bookingsKpi().subscribe((r) => (result = r));
    expect(result).toEqual({ total: 3, pending: 1, confirmed: 2 });
  });

  it('sums the expected spread for the reconciliation KPI', () => {
    const service = configure(
      {},
      {},
      {
        list: () =>
          of(
            page([
              { status: 'OPEN', expectedSpread: { amount: 100, currency: 'BRL' } } as never,
              { status: 'DISCREPANCY', expectedSpread: { amount: 35, currency: 'BRL' } } as never,
            ]),
          ),
      },
      {},
    );

    let result:
      | { total: number; open: number; discrepancy: number; expectedSpread: { amount: number } | null }
      | undefined;
    service.reconciliationKpi().subscribe((r) => (result = r));
    expect(result?.open).toBe(1);
    expect(result?.discrepancy).toBe(1);
    expect(result?.expectedSpread?.amount).toBe(135);
  });

  it('maps the prevailing rate for the exchange KPI', () => {
    const service = configure({}, {}, {}, {
      current: () =>
        of({ id: 'r1', currencyPair: 'USD/BRL', rate: 5.4, effectiveFrom: '', setBy: '', note: null }),
    });

    let result: { pair: string; rate: number } | undefined;
    service.exchangeKpi().subscribe((r) => (result = r));
    expect(result).toEqual({ pair: 'USD/BRL', rate: 5.4 });
  });
});
