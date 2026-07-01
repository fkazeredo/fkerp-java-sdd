import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BrandView } from './portfolio.models';
import { PortfolioService } from './portfolio.service';

const BRAND: BrandView = {
  id: 'b1',
  brandRef: 'ACME-AIR',
  displayName: 'Acme Airlines',
  status: 'ACTIVE',
  createdAt: '2026-06-01T00:00:00Z',
  updatedAt: '2026-06-01T00:00:00Z',
};

describe('PortfolioService', () => {
  let http: HttpTestingController;
  let service: PortfolioService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PortfolioService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PortfolioService);
  });

  afterEach(() => http.verify());

  it('lists brands with a status filter', () => {
    service.listBrands('ACTIVE').subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/portfolio/brands' && r.params.get('status') === 'ACTIVE',
    );
    req.flush([BRAND]);
  });

  it('registers a brand (POST /brands)', () => {
    service.registerBrand({ brandRef: 'ACME-AIR', displayName: 'Acme Airlines' }).subscribe();
    const req = http.expectOne('/api/portfolio/brands');
    expect(req.request.method).toBe('POST');
    req.flush(BRAND);
  });

  it('deactivates a brand (DELETE)', () => {
    service.deactivateBrand('b1').subscribe();
    const req = http.expectOne('/api/portfolio/brands/b1');
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...BRAND, status: 'INACTIVE' });
  });

  it('lists contracts and checks coverage', () => {
    service.contracts('ACME-AIR').subscribe();
    http.expectOne('/api/portfolio/brands/ACME-AIR/contracts').flush([]);

    service.contractCoverage('ACME-AIR').subscribe();
    http
      .expectOne('/api/portfolio/brands/ACME-AIR/contract-coverage')
      .flush({ brandRef: 'ACME-AIR', on: '2026-06-01', covered: true });
  });

  it('registers a contract (POST)', () => {
    service.registerContract('ACME-AIR', { validFrom: '2026-01-01' }).subscribe();
    const req = http.expectOne('/api/portfolio/brands/ACME-AIR/contracts');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('defines a goal and reads its progress', () => {
    service
      .defineGoal('ACME-AIR', {
        period: '2026',
        metric: 'REVENUE',
        target: { amount: 1000, currency: 'BRL' },
      })
      .subscribe();
    http.expectOne('/api/portfolio/brands/ACME-AIR/goals').flush({ id: 'g1' });

    service.goalProgress('b1', '2026').subscribe();
    http.expectOne('/api/portfolio/brands/b1/goals/2026/progress').flush({});
  });

  it('runs the expiry sweep (POST /flag-expiring)', () => {
    service.flagExpiring().subscribe();
    const req = http.expectOne('/api/portfolio/contracts/flag-expiring');
    expect(req.request.method).toBe('POST');
    req.flush({ flagged: 2 });
  });
});
