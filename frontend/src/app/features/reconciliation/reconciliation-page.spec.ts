import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { ReconciliationPage } from './reconciliation-page';
import { ReconciliationCaseView } from './reconciliation.models';
import { ReconciliationService } from './reconciliation.service';

const CASE: ReconciliationCaseView = {
  caseId: 'c1',
  bookingId: 'b1',
  baseAmount: { amount: 500, currency: 'USD' },
  pinnedRate: 5.4,
  baseBrl: { amount: 2700, currency: 'BRL' },
  expectedSupplierCommission: { amount: 405, currency: 'BRL' },
  expectedAgentCommission: { amount: 270, currency: 'BRL' },
  expectedSpread: { amount: 135, currency: 'BRL' },
  realizedSpread: null,
  fxGainLoss: null,
  discrepancy: { amount: 0, currency: 'BRL' },
  status: 'OPEN',
};

const PAGE: PageResponse<ReconciliationCaseView> = {
  content: [CASE],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function configure(service: Partial<ReconciliationService>): void {
  TestBed.configureTestingModule({
    imports: [ReconciliationPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: ReconciliationService, useValue: service },
    ],
  });
}

describe('ReconciliationPage', () => {
  it('lists cases on load', () => {
    configure({ list: () => of(PAGE) });
    const fixture = TestBed.createComponent(ReconciliationPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.cases().length).toBe(1);
  });

  it('shows the error state when listing fails', () => {
    configure({
      list: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(ReconciliationPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
  });
});
