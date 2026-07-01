import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MarketingService } from './marketing.service';

describe('MarketingService', () => {
  let http: HttpTestingController;
  let service: MarketingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MarketingService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(MarketingService);
  });

  afterEach(() => http.verify());

  it('reads consent state with subject query params', () => {
    service.consentState('acc-1', 'ACCOUNT').subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/marketing/consents' && r.params.get('subject') === 'acc-1',
    );
    expect(req.request.params.get('subjectType')).toBe('ACCOUNT');
    expect(req.request.params.get('purpose')).toBe('NEWSLETTER');
    req.flush({ current: null, history: [] });
  });

  it('grants consent (POST /consents)', () => {
    service
      .grantConsent({ subject: { id: 'acc-1', type: 'ACCOUNT' }, purpose: 'NEWSLETTER' })
      .subscribe();
    const req = http.expectOne('/api/marketing/consents');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('revokes consent by id (DELETE)', () => {
    service.revokeConsent('c1').subscribe();
    const req = http.expectOne('/api/marketing/consents/c1');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('defines a segment and previews it', () => {
    service.defineSegment({ name: 'B2B', criteria: { accountType: 'AGENCY' } }).subscribe();
    const post = http.expectOne('/api/marketing/segments');
    expect(post.request.method).toBe('POST');
    post.flush({ id: 's1', name: 'B2B', criteria: {}, createdAt: '', updatedAt: '' });

    service.previewSegment('s1').subscribe();
    http.expectOne('/api/marketing/segments/s1/preview').flush({ segmentId: 's1', reachable: 3 });
  });

  it('creates and sends a campaign', () => {
    service.createCampaign({ segmentId: 's1', code: 'PROMO' }).subscribe();
    http.expectOne('/api/marketing/campaigns').flush({});

    service.sendCampaign('cmp-1').subscribe();
    const send = http.expectOne('/api/marketing/campaigns/cmp-1/send');
    expect(send.request.method).toBe('POST');
    send.flush({ campaignId: 'cmp-1', targeted: 2, suppressedNoConsent: 1, queued: 1 });
  });

  it('registers and lists attribution', () => {
    service.registerAttribution({ campaignCode: 'PROMO', bookingId: 'bk-1' }).subscribe();
    http.expectOne('/api/marketing/attribution').flush({});

    service.attribution('PROMO').subscribe();
    const list = http.expectOne(
      (r) => r.url === '/api/marketing/attribution' && r.params.get('campaignCode') === 'PROMO',
    );
    list.flush([]);
  });

  it('erases a subject (POST /erasure)', () => {
    service.erase({ subjectId: 'acc-1', subjectType: 'ACCOUNT' }).subscribe();
    const req = http.expectOne('/api/marketing/erasure');
    expect(req.request.method).toBe('POST');
    req.flush({ subjectId: 'acc-1', anonymizedConsents: 2, suppressed: true });
  });
});
