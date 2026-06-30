import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { DashboardPage } from './dashboard-page';
import {
  AccountsKpi,
  BookingsKpi,
  DashboardService,
  ExchangeKpi,
  ReconciliationKpi,
} from './dashboard.service';

const ACCOUNTS: AccountsKpi = { total: 3, active: 2 };
const BOOKINGS: BookingsKpi = { total: 5, pending: 1, confirmed: 2 };
const RECON: ReconciliationKpi = {
  total: 4,
  open: 1,
  discrepancy: 1,
  expectedSpread: { amount: 135, currency: 'BRL' },
};
const EXCHANGE: ExchangeKpi = { pair: 'USD/BRL', rate: 5.4 };

function configure(service: Partial<DashboardService>): void {
  TestBed.configureTestingModule({
    imports: [DashboardPage],
    providers: [
      provideRouter([]),
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: DashboardService, useValue: service },
    ],
  });
}

describe('DashboardPage (BR10/AC10)', () => {
  it('loads all KPIs from the existing endpoints', () => {
    configure({
      accountsKpi: () => of(ACCOUNTS),
      bookingsKpi: () => of(BOOKINGS),
      reconciliationKpi: () => of(RECON),
      exchangeKpi: () => of(EXCHANGE),
    });
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    const page = fixture.componentInstance;
    expect(page.accounts.state()).toBe('success');
    expect(page.accounts.value()?.total).toBe(3);
    expect(page.bookings.value()?.confirmed).toBe(2);
    expect(page.reconciliation.value()?.discrepancy).toBe(1);
    expect(page.exchange.value()?.rate).toBe(5.4);
  });

  it('sets an independent error state per KPI (AC10)', () => {
    configure({
      accountsKpi: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
      bookingsKpi: () => of(BOOKINGS),
      reconciliationKpi: () => of(RECON),
      exchangeKpi: () => of(EXCHANGE),
    });
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    const page = fixture.componentInstance;
    expect(page.accounts.state()).toBe('error');
    expect(page.accounts.errorCode()).toBe('error.internal');
    // The other cards still load.
    expect(page.bookings.state()).toBe('success');
  });

  it('keeps the access.denied code for a card so its permission state shows (AC10)', () => {
    configure({
      accountsKpi: () => of(ACCOUNTS),
      bookingsKpi: () => of(BOOKINGS),
      reconciliationKpi: () => of(RECON),
      exchangeKpi: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.exchange.state()).toBe('error');
    expect(fixture.componentInstance.exchange.errorCode()).toBe('access.denied');
  });
});
