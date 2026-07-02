import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SourcingService } from './sourcing.service';
import { SourcedOfferView } from './sourcing.models';

const OFFER: SourcedOfferView = {
  id: 'o1',
  productText: 'City tour',
  basePrice: { amount: 120, currency: 'EUR' },
  origin: 'EXTERNAL_SITE',
  integrationLevel: 'NONE',
  externalRef: null,
  createdAt: '2026-06-01T00:00:00Z',
};

describe('SourcingService', () => {
  let http: HttpTestingController;
  let service: SourcingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), SourcingService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(SourcingService);
  });

  afterEach(() => http.verify());

  it('registers a sourced offer', () => {
    service
      .register({
        productText: 'City tour',
        basePrice: { amount: 120, currency: 'EUR' },
        origin: 'EXTERNAL_SITE',
        integrationLevel: 'NONE',
      })
      .subscribe();
    const req = http.expectOne('/api/sourcing/offers');
    expect(req.request.method).toBe('POST');
    req.flush(OFFER);
  });

  it('reads an offer by id', () => {
    service.getById('o1').subscribe();
    http.expectOne('/api/sourcing/offers/o1').flush(OFFER);
  });

  it('lists the inbound quarantine filtered by status (BR10/DL-0120)', () => {
    service.listQuarantine('QUARANTINED').subscribe();
    const req = http.expectOne('/api/sourcing/inbound-quarantine?status=QUARANTINED');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('replays a quarantined payload', () => {
    service.replayQuarantine('q1').subscribe();
    const req = http.expectOne('/api/sourcing/inbound-quarantine/q1/replay');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('discards a quarantined payload', () => {
    service.discardQuarantine('q1').subscribe();
    const req = http.expectOne('/api/sourcing/inbound-quarantine/q1/discard');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
