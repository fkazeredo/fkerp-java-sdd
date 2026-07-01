import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AfterSalesService } from './aftersales.service';
import { SupportCaseView } from './aftersales.models';

const CASE: SupportCaseView = {
  id: 'c1',
  bookingId: 'bk-1',
  type: 'COMPLAINT',
  status: 'OPEN',
  summary: null,
  openedAt: '2026-06-01T00:00:00Z',
  firstResponseDueAt: '2026-06-01T04:00:00Z',
  dueAt: '2026-06-03T00:00:00Z',
  breached: false,
  resolvedAt: null,
  resolution: null,
  linkedPayoutId: null,
  reopenCount: 0,
  costToServeTotal: { amount: 0, currency: 'BRL' },
};

describe('AfterSalesService', () => {
  let http: HttpTestingController;
  let service: AfterSalesService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AfterSalesService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AfterSalesService);
  });

  afterEach(() => http.verify());

  it('lists cases with filters as query params', () => {
    service
      .list({ type: 'REFUND_REQUEST', status: 'OPEN', bookingId: 'bk-1', breached: true })
      .subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/aftersales/cases' && r.params.get('type') === 'REFUND_REQUEST',
    );
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('bookingId')).toBe('bk-1');
    expect(req.request.params.get('breached')).toBe('true');
    req.flush({ content: [CASE], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('reads a case by id', () => {
    service.getById('c1').subscribe();
    http.expectOne('/api/aftersales/cases/c1').flush(CASE);
  });

  it('opens a case', () => {
    service.open({ bookingId: 'bk-1', type: 'COMPLAINT', summary: 'x' }).subscribe();
    const req = http.expectOne('/api/aftersales/cases');
    expect(req.request.method).toBe('POST');
    req.flush(CASE);
  });

  it('drives a transition', () => {
    service.transition('c1', 'assign').subscribe();
    const req = http.expectOne('/api/aftersales/cases/c1/assign');
    expect(req.request.method).toBe('POST');
    req.flush(CASE);
  });

  it('resolves a case', () => {
    service.resolve('c1', { resolution: 'REFUND_APPROVED' }).subscribe();
    const req = http.expectOne('/api/aftersales/cases/c1/resolve');
    expect(req.request.method).toBe('POST');
    req.flush(CASE);
  });
});
