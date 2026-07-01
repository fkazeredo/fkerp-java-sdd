import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AssetsService } from './assets.service';

describe('AssetsService', () => {
  let http: HttpTestingController;
  let service: AssetsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AssetsService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AssetsService);
  });

  afterEach(() => http.verify());

  it('lists assets with combinable filters', () => {
    service.list('SOFTWARE_LICENSE', 'ACTIVE', 30).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/assets' &&
        r.params.get('type') === 'SOFTWARE_LICENSE' &&
        r.params.get('status') === 'ACTIVE' &&
        r.params.get('expiringWithinDays') === '30',
    );
    req.flush([]);
  });

  it('registers an asset (POST /assets)', () => {
    service
      .register({
        type: 'EQUIPMENT',
        identifier: 'NB-1',
        acquisitionDate: '2026-01-01',
        acquisitionCost: { amount: 1000, currency: 'BRL' },
      })
      .subscribe();
    const req = http.expectOne('/api/assets');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('retires an asset (POST /retire)', () => {
    service.retire('a1', { reason: 'broken' }).subscribe();
    const req = http.expectOne('/api/assets/a1/retire');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('runs the expiry sweep (POST /flag-expiring)', () => {
    service.flagExpiring().subscribe();
    const req = http.expectOne('/api/assets/flag-expiring');
    expect(req.request.method).toBe('POST');
    req.flush({ flagged: 3 });
  });
});
