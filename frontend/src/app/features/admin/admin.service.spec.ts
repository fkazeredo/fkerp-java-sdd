import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let http: HttpTestingController;
  let service: AdminService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AdminService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AdminService);
  });

  afterEach(() => http.verify());

  it('lists suppliers with type/status filters', () => {
    service.listSuppliers('UTILITY', 'ACTIVE').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/admin/suppliers' &&
        r.params.get('type') === 'UTILITY' &&
        r.params.get('status') === 'ACTIVE',
    );
    req.flush([]);
  });

  it('registers a supplier (POST /suppliers)', () => {
    service.registerSupplier({ type: 'SERVICE', identifier: 'S-1', displayName: 'Acme' }).subscribe();
    const req = http.expectOne('/api/admin/suppliers');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('lists and registers a contract', () => {
    service.contracts('s1').subscribe();
    http.expectOne('/api/admin/suppliers/s1/contracts').flush([]);

    service.registerContract('s1', { validFrom: '2026-01-01', recurrence: 'MONTHLY' }).subscribe();
    const req = http.expectOne('/api/admin/suppliers/s1/contracts');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('registers a recurring expense (POST /expenses)', () => {
    service
      .registerExpense({
        supplierId: 's1',
        period: '2026-01',
        amount: { amount: 100, currency: 'BRL' },
        kind: 'UTILITY',
      })
      .subscribe();
    const req = http.expectOne('/api/admin/expenses');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('runs the contract-expiry sweep (POST /contracts/flag-expiring)', () => {
    service.flagExpiring().subscribe();
    const req = http.expectOne('/api/admin/contracts/flag-expiring');
    expect(req.request.method).toBe('POST');
    req.flush({ flagged: 1 });
  });
});
