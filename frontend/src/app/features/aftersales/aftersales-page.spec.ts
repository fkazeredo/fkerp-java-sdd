import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { AfterSalesPage } from './aftersales-page';
import { SupportCaseView } from './aftersales.models';
import { AfterSalesService } from './aftersales.service';

const CASE: SupportCaseView = {
  id: 'c1',
  bookingId: 'bk-1',
  type: 'REFUND_REQUEST',
  status: 'OPEN',
  summary: 'Cliente pediu reembolso',
  openedAt: '2026-06-01T00:00:00Z',
  firstResponseDueAt: '2026-06-01T04:00:00Z',
  dueAt: '2026-06-03T00:00:00Z',
  breached: false,
  resolvedAt: null,
  resolution: null,
  linkedPayoutId: null,
  reopenCount: 0,
  costToServeTotal: { amount: 0, currency: 'BRL' },
};

const PAGE: PageResponse<SupportCaseView> = {
  content: [CASE],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const EMPTY: PageResponse<SupportCaseView> = { ...PAGE, content: [], totalElements: 0 };

function configure(service: Partial<AfterSalesService>): void {
  TestBed.configureTestingModule({
    imports: [AfterSalesPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: AfterSalesService, useValue: service },
    ],
  });
}

describe('AfterSalesPage', () => {
  it('lists cases on load (loading → success)', () => {
    configure({ list: () => of(PAGE) });
    const fixture = TestBed.createComponent(AfterSalesPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.listState()).toBe('success');
    expect(fixture.componentInstance.cases().length).toBe(1);
  });

  it('collapses to the empty state when there are no cases', () => {
    configure({ list: () => of(EMPTY) });
    const fixture = TestBed.createComponent(AfterSalesPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('empty');
  });

  it('shows the error state when listing fails', () => {
    configure({
      list: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(AfterSalesPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('error.internal');
  });

  it('renders the permission state on a 403 (access.denied)', () => {
    configure({
      list: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(AfterSalesPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('opens a case, selects it and reloads the list', () => {
    const open = vi.fn(() => of(CASE));
    const list = vi.fn(() => of(PAGE));
    configure({ list, open });
    const fixture = TestBed.createComponent(AfterSalesPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newBookingId = 'bk-1';
    expect(page.isDirty()).toBe(true);
    page.open();

    expect(open).toHaveBeenCalled();
    expect(page.selected()?.id).toBe('c1');
    expect(list).toHaveBeenCalledTimes(2);
    expect(page.isDirty()).toBe(false);
  });

  it('drives a transition on the selected case', () => {
    const transition = vi.fn(() => of({ ...CASE, status: 'IN_PROGRESS' as const }));
    configure({ list: () => of(PAGE), transition });
    const fixture = TestBed.createComponent(AfterSalesPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.select(CASE);
    page.transition('assign');

    expect(transition).toHaveBeenCalledWith('c1', 'assign');
    expect(page.selected()?.status).toBe('IN_PROGRESS');
  });

  it('resolves the selected case and surfaces an action error by its code', () => {
    const resolve = vi.fn(() =>
      throwError(() => ({ code: 'aftersales.case.transition.invalid', message: '', fields: [] })),
    );
    configure({ list: () => of(PAGE), resolve });
    const fixture = TestBed.createComponent(AfterSalesPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.select(CASE);
    page.resolution = 'REFUND_APPROVED';
    page.resolveAmount = 100;
    page.resolve();

    expect(resolve).toHaveBeenCalled();
    expect(page.actionError()).toBe('aftersales.case.transition.invalid');
  });

  it('maps status severities', () => {
    configure({ list: () => of(PAGE) });
    const page = TestBed.createComponent(AfterSalesPage).componentInstance;

    expect(page.statusSeverity('RESOLVED')).toBe('success');
    expect(page.statusSeverity('IN_PROGRESS')).toBe('info');
    expect(page.statusSeverity('WAITING')).toBe('warn');
    expect(page.statusSeverity('OPEN')).toBe('secondary');
  });
});
