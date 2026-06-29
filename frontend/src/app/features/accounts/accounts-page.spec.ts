import { TestBed } from '@angular/core/testing';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { AccountsPage } from './accounts-page';
import { AccountResponse } from './accounts.models';
import { AccountsService } from './accounts.service';

const PAGE: PageResponse<AccountResponse> = {
  content: [
    {
      id: '1',
      legalType: 'CNPJ',
      documentNumber: '12345678000195',
      displayName: 'Agência Sol e Mar',
      status: 'ACTIVE',
      cadastur: null,
      iata: null,
      createdAt: '2026-06-29T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function configure(service: Partial<AccountsService>): void {
  TestBed.configureTestingModule({
    imports: [AccountsPage],
    providers: [
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: AccountsService, useValue: service },
    ],
  });
}

describe('AccountsPage', () => {
  it('lists accounts on load (success state)', () => {
    configure({ list: () => of(PAGE) });
    const fixture = TestBed.createComponent(AccountsPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('success');
    expect(fixture.componentInstance.accounts().length).toBe(1);
  });

  it('surfaces the error code when listing fails', () => {
    configure({
      list: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(AccountsPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('error.internal');
  });
});
