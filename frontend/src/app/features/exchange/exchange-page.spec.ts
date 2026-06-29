import { TestBed } from '@angular/core/testing';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { ExchangePage } from './exchange-page';
import { PinnedSellRateResponse } from './exchange.models';
import { ExchangeService } from './exchange.service';

const PAGE: PageResponse<PinnedSellRateResponse> = {
  content: [
    {
      id: '1',
      currencyPair: 'USD/BRL',
      rate: 5.4,
      effectiveFrom: '2026-06-29T00:00:00Z',
      setBy: 'dev',
      note: 'promo',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function configure(service: Partial<ExchangeService>): void {
  TestBed.configureTestingModule({
    imports: [ExchangePage],
    providers: [
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: ExchangeService, useValue: service },
    ],
  });
}

describe('ExchangePage', () => {
  it('loads the history (success state)', () => {
    configure({ history: () => of(PAGE) });
    const fixture = TestBed.createComponent(ExchangePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.rates().length).toBe(1);
  });

  it('shows the error state when the history request fails', () => {
    configure({
      history: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(ExchangePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
  });
});
