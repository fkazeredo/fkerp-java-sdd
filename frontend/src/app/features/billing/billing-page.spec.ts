import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { BillingPage } from './billing-page';
import { CommissionInvoiceView } from './billing.models';
import { BillingService } from './billing.service';

const DRAFT: CommissionInvoiceView = {
  id: 'inv-1',
  commissionEntryId: 'ce-1',
  base: { amount: 300, currency: 'BRL' },
  status: 'RASCUNHO',
  iss: null,
  withholdings: [],
  regime: null,
  municipality: '3550308',
  serviceCode: null,
  number: null,
  verificationCode: null,
  documentId: null,
  createdAt: '2026-06-01T00:00:00Z',
};

function configure(service: Partial<BillingService>): void {
  TestBed.configureTestingModule({
    imports: [BillingPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: BillingService, useValue: service },
    ],
  });
}

describe('BillingPage', () => {
  it('starts idle before any lookup (empty screen)', () => {
    configure({});
    const fixture = TestBed.createComponent(BillingPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('idle');
    expect(fixture.componentInstance.invoice()).toBeNull();
  });

  it('looks up an invoice by id (loading → success)', () => {
    configure({ getById: () => of(DRAFT) });
    const fixture = TestBed.createComponent(BillingPage);
    const page = fixture.componentInstance;

    page.invoiceId = 'inv-1';
    page.lookup();

    expect(page.state()).toBe('success');
    expect(page.invoice()?.id).toBe('inv-1');
  });

  it('shows the error state when the lookup fails', () => {
    configure({
      getById: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(BillingPage);
    const page = fixture.componentInstance;

    page.invoiceId = 'inv-x';
    page.lookup();

    expect(page.state()).toBe('error');
    expect(page.errorCode()).toBe('error.internal');
  });

  it('creates a draft and selects it (dirty form is tracked)', () => {
    const createDraft = vi.fn(() => of(DRAFT));
    configure({ createDraft });
    const fixture = TestBed.createComponent(BillingPage);
    const page = fixture.componentInstance;

    page.draftCommissionEntryId = 'ce-1';
    page.draftBase = 300;
    page.draftMunicipality = '3550308';
    expect(page.isDirty()).toBe(true);
    page.createDraft();

    expect(createDraft).toHaveBeenCalled();
    expect(page.invoice()?.id).toBe('inv-1');
    expect(page.state()).toBe('success');
  });

  it('issues a draft and renders the issued invoice', () => {
    const issued: CommissionInvoiceView = {
      ...DRAFT,
      status: 'EMITIDA',
      iss: { amount: 15, currency: 'BRL' },
      withholdings: [{ kind: 'IRRF', amount: { amount: 4.5, currency: 'BRL' } }],
      number: 'NF-1',
      verificationCode: 'VC-1',
      documentId: 'doc-1',
    };
    configure({ getById: () => of(DRAFT), issue: () => of(issued) });
    const fixture = TestBed.createComponent(BillingPage);
    const page = fixture.componentInstance;

    page.invoiceId = 'inv-1';
    page.lookup();
    fixture.detectChanges();
    page.issue();
    fixture.detectChanges();

    expect(page.invoice()?.status).toBe('EMITIDA');
    expect(page.statusSeverity('EMITIDA')).toBe('success');
    expect(page.statusSeverity('CANCELADA')).toBe('danger');
    expect(page.statusSeverity('RASCUNHO')).toBe('secondary');
  });

  it('cancels an issued invoice with a reason', () => {
    const issued: CommissionInvoiceView = { ...DRAFT, status: 'EMITIDA' };
    const cancelled: CommissionInvoiceView = { ...DRAFT, status: 'CANCELADA' };
    const cancel = vi.fn(() => of(cancelled));
    configure({ getById: () => of(issued), cancel });
    const fixture = TestBed.createComponent(BillingPage);
    const page = fixture.componentInstance;

    page.invoiceId = 'inv-1';
    page.lookup();
    page.cancelReason = 'wrong base';
    page.cancel();

    expect(cancel).toHaveBeenCalledWith('inv-1', { reason: 'wrong base' });
    expect(page.invoice()?.status).toBe('CANCELADA');
  });

  it('renders the permission state when issuing is denied (403)', () => {
    configure({
      getById: () => of(DRAFT),
      issue: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(BillingPage);
    const page = fixture.componentInstance;

    page.invoiceId = 'inv-1';
    page.lookup();
    page.issue();

    expect(page.actionError()).toBe('access.denied');
  });
});
