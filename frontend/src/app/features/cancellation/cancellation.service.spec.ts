import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CancellationService } from './cancellation.service';
import { CancellationPolicyView } from './cancellation.models';

const POLICY: CancellationPolicyView = {
  scopeRef: 'HOTEL-XYZ',
  type: 'STANDARD',
  windows: [],
  refundable: true,
  costBearer: 'AGENCY',
  merchantOfRecord: false,
  noShowFee: null,
  waivedIfFlightCancelled: false,
};

describe('CancellationService', () => {
  let http: HttpTestingController;
  let service: CancellationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CancellationService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CancellationService);
  });

  afterEach(() => http.verify());

  it('reads the policy for a scope, encoding the ref', () => {
    service.get('HOTEL XYZ').subscribe();
    const req = http.expectOne('/api/products/HOTEL%20XYZ/cancellation-policy');
    expect(req.request.method).toBe('GET');
    req.flush(POLICY);
  });

  it('administers (PUT) the policy', () => {
    service
      .put('HOTEL-XYZ', {
        type: 'STANDARD',
        windows: [],
        refundable: true,
        costBearer: 'AGENCY',
      })
      .subscribe();
    const req = http.expectOne('/api/products/HOTEL-XYZ/cancellation-policy');
    expect(req.request.method).toBe('PUT');
    req.flush(POLICY);
  });
});
