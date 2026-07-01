import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { PayoutPage } from './payout-page';
import { PayoutView } from './payout.models';
import { PayoutService } from './payout.service';

const PAYOUT: PayoutView = {
  id: 'p1',
  kind: 'AGENT_COMMISSION',
  payee: { id: 'ag-1', type: 'AGENT' },
  bookingId: null,
  originRef: null,
  amount: { amount: 200, currency: 'BRL' },
  settlementRate: null,
  settledBrl: null,
  status: 'PENDING',
  proofDocumentId: null,
  installments: [
    {
      id: 'i1',
      seq: 1,
      dueDate: '2026-06-30',
      amount: { amount: 200, currency: 'BRL' },
      status: 'PENDING',
      executedAt: null,
      proofDocumentId: null,
    },
  ],
  createdAt: '2026-06-01T00:00:00Z',
};

const PAGE: PageResponse<PayoutView> = {
  content: [PAYOUT],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const EMPTY: PageResponse<PayoutView> = { ...PAGE, content: [], totalElements: 0 };

function configure(service: Partial<PayoutService>): void {
  TestBed.configureTestingModule({
    imports: [PayoutPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: PayoutService, useValue: service },
    ],
  });
}

describe('PayoutPage', () => {
  it('lists payouts on load (loading → success)', () => {
    configure({ list: () => of(PAGE) });
    const fixture = TestBed.createComponent(PayoutPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.payouts().length).toBe(1);
  });

  it('collapses to the empty state when there are no payouts', () => {
    configure({ list: () => of(EMPTY) });
    const fixture = TestBed.createComponent(PayoutPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('empty');
  });

  it('shows the error state when listing fails', () => {
    configure({
      list: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PayoutPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
  });

  it('creates a payout and selects it (dirty form is tracked)', () => {
    const create = vi.fn(() => of(PAYOUT));
    const list = vi.fn(() => of(PAGE));
    configure({ list, create });
    const fixture = TestBed.createComponent(PayoutPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newPayeeId = 'ag-1';
    page.newAmount = 200;
    expect(page.isDirty()).toBe(true);
    page.create();

    expect(create).toHaveBeenCalled();
    expect(page.selected()?.id).toBe('p1');
    expect(page.newAmount).toBeNull();
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('surfaces a create error by its code', () => {
    configure({
      list: () => of(PAGE),
      create: () =>
        throwError(() => ({ code: 'payout.refund.origin-required', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PayoutPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newPayeeId = 'c-1';
    page.newAmount = 50;
    page.create();

    expect(page.createError()).toBe('payout.refund.origin-required');
  });

  it('selects a payout, renders the detail and executes a success', () => {
    const executed: PayoutView = { ...PAYOUT, status: 'EXECUTED' };
    configure({ list: () => of(PAGE), execute: () => of(executed) });
    const fixture = TestBed.createComponent(PayoutPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.select(PAYOUT);
    fixture.detectChanges();
    page.execute();

    expect(page.selected()?.status).toBe('EXECUTED');
    expect(page.statusSeverity('EXECUTED')).toBe('success');
    expect(page.statusSeverity('FAILED')).toBe('danger');
    expect(page.statusSeverity('EXECUTING')).toBe('warn');
    expect(page.statusSeverity('PENDING')).toBe('secondary');
  });

  it('reflects an explicit FAILED outcome on execute without a false positive', () => {
    const failed: PayoutView = { ...PAYOUT, status: 'FAILED' };
    configure({ list: () => of(PAGE), execute: () => of(failed) });
    const fixture = TestBed.createComponent(PayoutPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.select(PAYOUT);
    page.outcomeHint = 'FAILED';
    page.execute();

    expect(page.selected()?.status).toBe('FAILED');
  });
});
