import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InsightView } from './intelligence.models';
import { IntelligenceService } from './intelligence.service';

const INSIGHT: InsightView = {
  id: 'i1',
  type: 'PROMO_FX_ADVISOR',
  subjectKind: 'AGENCY',
  subjectRef: 'acc-1',
  evidence: {
    accruedSubsidy: { amount: 100, currency: 'BRL' },
    realizedGap: { amount: 20, currency: 'BRL' },
    volumeAttracted: 5,
    sources: ['BookingConfirmed'],
  },
  recommendation: {
    verdict: 'CONVERTE',
    action: 'Manter a promoção',
    estimatedGain: { amount: 50, currency: 'BRL' },
    estimatedRisk: null,
  },
  guardrail: null,
  status: 'NEW',
  generatedAt: '2026-06-01T00:00:00Z',
  decidedBy: null,
  decidedAt: null,
};

describe('IntelligenceService', () => {
  let http: HttpTestingController;
  let service: IntelligenceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), IntelligenceService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(IntelligenceService);
  });

  afterEach(() => http.verify());

  it('lists insights with filters as query params', () => {
    service.list({ type: 'PROMO_FX_ADVISOR', status: 'NEW', subjectRef: 'acc-1' }).subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/intelligence/insights' && r.params.get('type') === 'PROMO_FX_ADVISOR',
    );
    expect(req.request.params.get('status')).toBe('NEW');
    expect(req.request.params.get('subjectRef')).toBe('acc-1');
    req.flush({ content: [INSIGHT], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('reads an insight by id', () => {
    service.getById('i1').subscribe();
    http.expectOne('/api/intelligence/insights/i1').flush(INSIGHT);
  });

  it('records a decision', () => {
    service.decide('i1', { decision: 'ACCEPTED', note: 'ok' }).subscribe();
    const req = http.expectOne('/api/intelligence/insights/i1/decision');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.decision).toBe('ACCEPTED');
    req.flush({ ...INSIGHT, status: 'ACCEPTED' });
  });
});
