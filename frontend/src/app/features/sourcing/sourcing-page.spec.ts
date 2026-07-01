import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { SourcingPage } from './sourcing-page';
import { SourcedOfferView } from './sourcing.models';
import { SourcingService } from './sourcing.service';

const OFFER: SourcedOfferView = {
  id: 'o1',
  productText: 'City tour em Lisboa',
  basePrice: { amount: 120, currency: 'EUR' },
  origin: 'EXTERNAL_SITE',
  integrationLevel: 'NONE',
  externalRef: 'ext-9',
  createdAt: '2026-06-01T00:00:00Z',
};

function configure(service: Partial<SourcingService>): void {
  TestBed.configureTestingModule({
    imports: [SourcingPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: SourcingService, useValue: service },
    ],
  });
}

describe('SourcingPage', () => {
  it('starts idle before any lookup', () => {
    configure({});
    const fixture = TestBed.createComponent(SourcingPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('idle');
  });

  it('looks up an offer by id (loading → success)', () => {
    configure({ getById: () => of(OFFER) });
    const fixture = TestBed.createComponent(SourcingPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.lookupId = 'o1';
    page.lookup();

    expect(page.state()).toBe('success');
    expect(page.offer()?.origin).toBe('EXTERNAL_SITE');
  });

  it('shows the error state when the lookup fails', () => {
    configure({
      getById: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(SourcingPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.lookupId = 'missing';
    page.lookup();

    expect(page.state()).toBe('error');
    expect(page.errorCode()).toBe('error.internal');
  });

  it('renders the permission state on a 403 (access.denied)', () => {
    configure({
      getById: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(SourcingPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.lookupId = 'x';
    page.lookup();

    expect(page.errorCode()).toBe('access.denied');
  });

  it('registers an offer, shows it and resets the dirty form', () => {
    const register = vi.fn(() => of(OFFER));
    configure({ register });
    const fixture = TestBed.createComponent(SourcingPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newProductText = 'City tour em Lisboa';
    page.newAmount = 120;
    expect(page.isDirty()).toBe(true);
    page.register();

    expect(register).toHaveBeenCalled();
    expect(page.offer()?.id).toBe('o1');
    expect(page.state()).toBe('success');
    expect(page.isDirty()).toBe(false);
  });

  it('surfaces a register error by its code', () => {
    configure({
      register: () => throwError(() => ({ code: 'sourcing.offer.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(SourcingPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newProductText = 'x';
    page.newAmount = 1;
    page.register();

    expect(page.registerError()).toBe('sourcing.offer.invalid');
  });

  it('maps integration-level severities', () => {
    configure({});
    const page = TestBed.createComponent(SourcingPage).componentInstance;

    expect(page.integrationSeverity('BIDIRECTIONAL')).toBe('success');
    expect(page.integrationSeverity('INBOUND')).toBe('info');
    expect(page.integrationSeverity('NONE')).toBe('secondary');
  });
});
