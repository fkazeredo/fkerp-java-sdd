import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
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
      provideNoopAnimations(),
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

  it('collapses to the empty state when the list is empty (AC9)', () => {
    configure({ list: () => of({ ...PAGE, content: [], totalElements: 0 }) });
    const fixture = TestBed.createComponent(AccountsPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('empty');
  });

  it('keeps the access.denied code so the permission state shows on 403 (AC9)', () => {
    configure({
      list: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(AccountsPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.listState()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('reports a dirty form when the document field is filled (AC8/BR9)', () => {
    configure({ list: () => of(PAGE) });
    const fixture = TestBed.createComponent(AccountsPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.isDirty()).toBe(false);
    fixture.componentInstance.documentNumber = '123';
    expect(fixture.componentInstance.isDirty()).toBe(true);
  });
});
