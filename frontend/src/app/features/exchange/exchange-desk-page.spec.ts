import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { ExchangeDeskPage } from './exchange-desk-page';
import {
  FxPositionView,
  LiveExposureView,
  MarketRateResponse,
  PromoFxResultView,
} from './exchange.models';
import { ExchangeDeskService } from './exchange-desk.service';

const EXPOSURE: LiveExposureView = {
  asOf: '2026-06-30T00:00:00Z',
  openPositions: 2,
  accruedSubsidy: { amount: 100, currency: 'BRL' },
  markToMarketDrift: { amount: 10, currency: 'BRL' },
  totalExposure: { amount: 110, currency: 'BRL' },
  driftThreshold: { amount: 50, currency: 'BRL' },
  driftAlert: false,
  openForwards: 0,
  unhedgedExposureBase: { amount: 2500, currency: 'BRL' },
};

const RATE: MarketRateResponse = {
  id: 'r1',
  currencyPair: 'USD/BRL',
  rate: 5.4,
  observedAt: '2026-06-30T00:00:00Z',
  source: 'MANUAL',
};

const RATE_PAGE: PageResponse<MarketRateResponse> = {
  content: [RATE],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const POSITION: FxPositionView = {
  bookingId: 'bk-1',
  foreignAmount: { amount: 200, currency: 'USD' },
  pinnedRate: 5.4,
  marketAtFreeze: 5.3,
  subsidy: { amount: -20, currency: 'BRL' },
  markToMarketDrift: { amount: 5, currency: 'BRL' },
  settlementRate: null,
  realizedDrift: null,
  totalGap: null,
  status: 'OPEN',
  openedAt: '2026-06-01T00:00:00Z',
};

const PROMO: PromoFxResultView = {
  period: '2026-06',
  positions: 3,
  subsidy: { amount: -60, currency: 'BRL' },
  drift: { amount: 15, currency: 'BRL' },
  totalGap: { amount: -45, currency: 'BRL' },
};

/** A service stub that satisfies the on-init loads by default. */
function baseService(overrides: Partial<ExchangeDeskService> = {}): Partial<ExchangeDeskService> {
  return {
    liveExposure: () => of(EXPOSURE),
    marketRateHistory: () => of(RATE_PAGE),
    listForwards: () => of([]),
    ...overrides,
  };
}

function configure(service: Partial<ExchangeDeskService>): void {
  TestBed.configureTestingModule({
    imports: [ExchangeDeskPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: ExchangeDeskService, useValue: service },
    ],
  });
}

describe('ExchangeDeskPage', () => {
  it('loads exposure and rate history on init (success)', () => {
    configure(baseService());
    const fixture = TestBed.createComponent(ExchangeDeskPage);

    fixture.detectChanges();
    const page = fixture.componentInstance;

    expect(page.exposureState()).toBe('success');
    expect(page.exposure()?.openPositions).toBe(2);
    expect(page.rateListState()).toBe('success');
    expect(page.rates().length).toBe(1);
  });

  it('collapses the rate history to the empty state', () => {
    configure(
      baseService({ marketRateHistory: () => of({ ...RATE_PAGE, content: [], totalElements: 0 }) }),
    );
    const fixture = TestBed.createComponent(ExchangeDeskPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.rateListState()).toBe('empty');
  });

  it('shows the exposure error state when the load fails', () => {
    configure(
      baseService({
        liveExposure: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(ExchangeDeskPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.exposureState()).toBe('error');
    expect(fixture.componentInstance.exposureError()).toBe('error.internal');
  });

  it('renders the permission state on a 403 exposure load', () => {
    configure(
      baseService({
        liveExposure: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(ExchangeDeskPage);

    fixture.detectChanges();

    expect(fixture.componentInstance.exposureError()).toBe('access.denied');
  });

  it('records a market rate then reloads the history', () => {
    const recordMarketRate = vi.fn(() => of(RATE));
    const marketRateHistory = vi.fn(() => of(RATE_PAGE));
    configure(baseService({ recordMarketRate, marketRateHistory }));
    const fixture = TestBed.createComponent(ExchangeDeskPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.newRate = 5.5;
    expect(page.isDirty()).toBe(true);
    page.recordRate();

    expect(recordMarketRate).toHaveBeenCalled();
    expect(page.newRate).toBeNull();
    // once on init + once after record
    expect(marketRateHistory).toHaveBeenCalledTimes(2);
  });

  it('looks up a position by booking (idle → success)', () => {
    configure(baseService({ positionByBooking: () => of(POSITION) }));
    const fixture = TestBed.createComponent(ExchangeDeskPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    expect(page.positionState()).toBe('idle');
    page.positionBookingId = 'bk-1';
    page.lookupPosition();

    expect(page.positionState()).toBe('success');
    expect(page.position()?.status).toBe('OPEN');
  });

  it('looks up the promo-fx report and surfaces an error by its code', () => {
    configure(
      baseService({
        promoFx: () => throwError(() => ({ code: 'exchange.period.invalid', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(ExchangeDeskPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.promoPeriod = 'bad';
    page.lookupPromo();

    expect(page.promoState()).toBe('error');
    expect(page.promoError()).toBe('exchange.period.invalid');
  });

  it('shows the promo-fx result on success', () => {
    configure(baseService({ promoFx: () => of(PROMO) }));
    const fixture = TestBed.createComponent(ExchangeDeskPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    page.promoPeriod = '2026-06';
    page.lookupPromo();

    expect(page.promoState()).toBe('success');
    expect(page.promo()?.positions).toBe(3);
  });
});
