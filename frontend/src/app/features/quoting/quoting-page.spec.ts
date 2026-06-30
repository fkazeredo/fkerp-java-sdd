import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { QuotingPage } from './quoting-page';
import { QuoteView } from './quoting.models';
import { QuotingService } from './quoting.service';

const QUOTE: QuoteView = {
  id: 'q1',
  accountId: 'a1',
  priceOrigin: 'MANUAL',
  basePrice: { amount: 500, currency: 'USD' },
  fxRate: 5.4,
  baseConverted: { amount: 2700, currency: 'BRL' },
  commission: {
    supplier: { amount: 405, currency: 'BRL' },
    agent: { amount: 270, currency: 'BRL' },
    spread: { amount: 135, currency: 'BRL' },
    spreadNegative: false,
  },
  markup: { pct: 0, amount: { amount: 0, currency: 'BRL' }, source: 'SYSTEM_DEFAULT' },
  suggestedAmount: { amount: 2700, currency: 'BRL' },
  appliedAmount: { amount: 2700, currency: 'BRL' },
  status: 'COMPOSED',
  validUntil: null,
  provenance: { rateId: 'r1', policySource: 'SYSTEM_DEFAULT' },
  overrides: [],
};

function configure(service: Partial<QuotingService>): void {
  TestBed.configureTestingModule({
    imports: [QuotingPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: QuotingService, useValue: service },
    ],
  });
}

describe('QuotingPage', () => {
  it('composes and shows the suggested amount', () => {
    configure({ compose: () => of(QUOTE) });
    const fixture = TestBed.createComponent(QuotingPage);
    fixture.detectChanges();

    fixture.componentInstance.compose();

    expect(fixture.componentInstance.quote()?.suggestedAmount.amount).toBe(2700);
  });

  it('surfaces the rate-missing error code', () => {
    configure({
      compose: () => throwError(() => ({ code: 'quoting.rate.missing', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(QuotingPage);
    fixture.detectChanges();

    fixture.componentInstance.compose();

    expect(fixture.componentInstance.composeError()).toBe('quoting.rate.missing');
  });
});
