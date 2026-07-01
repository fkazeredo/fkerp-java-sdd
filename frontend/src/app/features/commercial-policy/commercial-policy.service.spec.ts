import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ParameterRuleResponse, ResolvedParameterResponse } from './commercial-policy.models';
import { CommercialPolicyService } from './commercial-policy.service';

const RESOLVED: ResolvedParameterResponse = {
  key: 'MARKUP_PCT',
  value: '0.12',
  type: 'PERCENT',
  provenance: {
    layer: 'POLICY',
    ruleId: 'r1',
    definedBy: 'policy',
    definedAt: '2026-06-01T00:00:00Z',
    validUntil: null,
  },
};

const RULE: ParameterRuleResponse = {
  id: 'r1',
  key: 'MARKUP_PCT',
  layer: 'POLICY',
  accountId: null,
  productRef: null,
  channel: null,
  value: '0.12',
  type: 'PERCENT',
  validFrom: '2026-01-01',
  validUntil: null,
  definedBy: 'policy',
  justification: null,
  createdAt: '2026-06-01T00:00:00Z',
};

describe('CommercialPolicyService', () => {
  let http: HttpTestingController;
  let service: CommercialPolicyService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CommercialPolicyService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CommercialPolicyService);
  });

  afterEach(() => http.verify());

  it('resolves a parameter with scope query params', () => {
    service.resolve({ key: 'MARKUP_PCT', productRef: 'HTL-1', channel: 'WEB' }).subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/commercial-policy/resolve' && r.params.get('key') === 'MARKUP_PCT',
    );
    expect(req.request.params.get('productRef')).toBe('HTL-1');
    expect(req.request.params.get('channel')).toBe('WEB');
    req.flush(RESOLVED);
  });

  it('lists rules with key and layer filters', () => {
    service.listRules({ key: 'MARKUP_PCT', layer: 'POLICY' }).subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/commercial-policy/rules' && r.params.get('key') === 'MARKUP_PCT',
    );
    expect(req.request.params.get('layer')).toBe('POLICY');
    req.flush([RULE]);
  });

  it('defines a rule (POST /rules)', () => {
    service
      .defineRule({ key: 'MARKUP_PCT', layer: 'POLICY', value: '0.12', type: 'PERCENT' })
      .subscribe();
    const req = http.expectOne('/api/commercial-policy/rules');
    expect(req.request.method).toBe('POST');
    req.flush(RULE);
  });

  it('issues a directive (POST /directives)', () => {
    service
      .issueDirective({
        key: 'MARKUP_PCT',
        value: '0.20',
        type: 'PERCENT',
        justification: 'campanha',
      })
      .subscribe();
    const req = http.expectOne('/api/commercial-policy/directives');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.justification).toBe('campanha');
    req.flush({ ...RULE, layer: 'DIRECTIVE', justification: 'campanha' });
  });
});
