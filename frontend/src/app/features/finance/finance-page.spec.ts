import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { FinancePage } from './finance-page';
import { LedgerEntryView } from './finance.models';
import { FinanceService } from './finance.service';

const ENTRY: LedgerEntryView = {
  id: 'e1',
  direction: 'PAYABLE',
  party: { id: 'sup-1', type: 'SUPPLIER' },
  amount: { amount: 100, currency: 'BRL' },
  entryType: 'COMMISSION_PAYABLE',
  period: '2026-06',
  status: 'PROVISIONAL',
  documentRef: null,
  createdAt: '2026-06-01T00:00:00Z',
};

const PAGE: PageResponse<LedgerEntryView> = {
  content: [ENTRY],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const EMPTY: PageResponse<LedgerEntryView> = { ...PAGE, content: [], totalElements: 0 };

function configure(service: Partial<FinanceService>): void {
  TestBed.configureTestingModule({
    imports: [FinancePage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: FinanceService, useValue: service },
    ],
  });
}

describe('FinancePage', () => {
  it('lists ledger entries on load (loading → success)', () => {
    configure({ listEntries: () => of(PAGE) });
    const fixture = TestBed.createComponent(FinancePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.listState()).toBe('success');
    expect(fixture.componentInstance.entries().length).toBe(1);
  });

  it('collapses to the empty state when there are no entries', () => {
    configure({ listEntries: () => of(EMPTY) });
    const fixture = TestBed.createComponent(FinancePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('empty');
  });

  it('shows the error state when listing fails', () => {
    configure({
      listEntries: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(FinancePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('error.internal');
  });

  it('renders the permission state on a 403 (access.denied)', () => {
    configure({
      listEntries: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(FinancePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('creates an entry and reloads the ledger, then clears the amount', () => {
    const createEntry = vi.fn(() => of(ENTRY));
    const listEntries = vi.fn(() => of(PAGE));
    configure({ listEntries, createEntry });
    const fixture = TestBed.createComponent(FinancePage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newPartyId = 'sup-1';
    page.newAmount = 100;
    page.newPeriod = '2026-06';
    expect(page.isDirty()).toBe(true);
    page.submit();

    expect(createEntry).toHaveBeenCalled();
    expect(page.newAmount).toBeNull();
    expect(listEntries).toHaveBeenCalledTimes(2);
    expect(page.isDirty()).toBe(false);
  });

  it('surfaces a create error by its code', () => {
    configure({
      listEntries: () => of(PAGE),
      createEntry: () => throwError(() => ({ code: 'finance.party.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(FinancePage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newPartyId = 'x';
    page.newAmount = 1;
    page.newPeriod = '2026-06';
    page.submit();

    expect(page.submitError()).toBe('finance.party.invalid');
  });

  it('looks up a period with its trial balance and closes it', () => {
    const closed = {
      period: '2026-06',
      status: 'CLOSED' as const,
      payableTotals: [],
      receivableTotals: [],
      closedAt: '2026-07-01T00:00:00Z',
    };
    const closePeriod = vi.fn(() => of(closed));
    // After the close the screen re-looks-up the period, which now returns CLOSED.
    const getPeriod = vi.fn(() => of(closed));
    configure({
      listEntries: () => of(PAGE),
      getPeriod,
      trialBalance: () =>
        of({
          period: '2026-06',
          status: 'CLOSED',
          balances: [{ currency: 'BRL', payable: 100, receivable: 50, net: -50 }],
          provisionalCount: 1,
          confirmedCount: 0,
          settledCount: 0,
        }),
      closePeriod,
    });
    const fixture = TestBed.createComponent(FinancePage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.periodQuery = '2026-06';
    page.lookupPeriod();
    fixture.detectChanges();
    expect(page.trialBalance()?.balances.length).toBe(1);

    page.closePeriod();
    expect(closePeriod).toHaveBeenCalledWith('2026-06');
    expect(page.period()?.status).toBe('CLOSED');
  });

  it('maps status and period severities', () => {
    configure({ listEntries: () => of(PAGE) });
    const page = TestBed.createComponent(FinancePage).componentInstance;

    expect(page.statusSeverity('SETTLED')).toBe('success');
    expect(page.statusSeverity('CONFIRMED')).toBe('info');
    expect(page.statusSeverity('PROVISIONAL')).toBe('secondary');
    expect(page.periodSeverity('CLOSED')).toBe('success');
    expect(page.periodSeverity('CLOSING')).toBe('warn');
    expect(page.periodSeverity('OPEN')).toBe('info');
  });

  it('surfaces a vetoed close by its stable error code', () => {
    configure({
      listEntries: () => of(PAGE),
      getPeriod: () =>
        of({ period: '2026-06', status: 'OPEN', payableTotals: [], receivableTotals: [], closedAt: null }),
      trialBalance: () =>
        of({
          period: '2026-06',
          status: 'OPEN',
          balances: [],
          provisionalCount: 1,
          confirmedCount: 0,
          settledCount: 0,
        }),
      closePeriod: () =>
        throwError(() => ({ code: 'finance.period.cannot-close', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(FinancePage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.periodQuery = '2026-06';
    page.lookupPeriod();
    expect(page.periodState()).toBe('success');

    page.closePeriod();
    expect(page.closeError()).toBe('finance.period.cannot-close');
  });
});
