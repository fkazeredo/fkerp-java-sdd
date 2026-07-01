import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExchangeDeskService } from './exchange-desk.service';

describe('ExchangeDeskService', () => {
  let http: HttpTestingController;
  let service: ExchangeDeskService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ExchangeDeskService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ExchangeDeskService);
  });

  afterEach(() => http.verify());

  it('records a manual market rate', () => {
    service.recordMarketRate({ currencyPair: 'USD/BRL', rate: 5.4 }).subscribe();
    const req = http.expectOne('/api/exchange/market-rates');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('reads the current market rate with a pair param', () => {
    service.currentMarketRate('USD/BRL').subscribe();
    const req = http.expectOne((r) => r.url === '/api/exchange/market-rates/current');
    expect(req.request.params.get('pair')).toBe('USD/BRL');
    req.flush({});
  });

  it('reads the market-rate history with a pair param', () => {
    service.marketRateHistory('USD/BRL').subscribe();
    const req = http.expectOne((r) => r.url === '/api/exchange/market-rates');
    expect(req.request.params.get('pair')).toBe('USD/BRL');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('reads a position by booking', () => {
    service.positionByBooking('bk-1').subscribe();
    http.expectOne('/api/exchange/positions/bk-1').flush({});
  });

  it('reads the live exposure', () => {
    service.liveExposure().subscribe();
    http.expectOne('/api/exchange/exposure').flush({});
  });

  it('reads the promo-fx report with a period param', () => {
    service.promoFx('2026-06').subscribe();
    const req = http.expectOne((r) => r.url === '/api/exchange/reports/promo-fx');
    expect(req.request.params.get('period')).toBe('2026-06');
    req.flush({});
  });
});
