import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CadastroLabelService } from './cadastro-label.service';

describe('CadastroLabelService', () => {
  let http: HttpTestingController;
  let service: CadastroLabelService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CadastroLabelService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CadastroLabelService);
  });

  afterEach(() => http.verify());

  it('loads a type once and resolves the label from the code', () => {
    service.ensureLoaded('GOAL_METRIC');
    const req = http.expectOne(
      (r) => r.url === '/api/cadastro/items' && r.params.get('type') === 'GOAL_METRIC',
    );
    req.flush([
      { id: '1', type: 'GOAL_METRIC', code: 'REVENUE', label: 'Receita (spread BRL)', active: true, sortOrder: 20, createdAt: '' },
      { id: '2', type: 'GOAL_METRIC', code: 'VOLUME', label: 'Volume (vendas)', active: true, sortOrder: 10, createdAt: '' },
    ]);

    expect(service.label('GOAL_METRIC', 'REVENUE')).toBe('Receita (spread BRL)');
    expect(service.label('GOAL_METRIC', 'VOLUME')).toBe('Volume (vendas)');
  });

  it('does not re-request a type already loaded', () => {
    service.ensureLoaded('INSIGHT_TYPE');
    const req = http.expectOne((r) => r.params.get('type') === 'INSIGHT_TYPE');
    req.flush([]);

    service.ensureLoaded('INSIGHT_TYPE');
    http.expectNone((r) => r.params.get('type') === 'INSIGHT_TYPE');
  });

  it('falls back to the code when the type has not loaded or the code is unknown', () => {
    expect(service.label('CONSENT_PURPOSE', 'NEWSLETTER')).toBe('NEWSLETTER');

    service.ensureLoaded('CONSENT_PURPOSE');
    http
      .expectOne((r) => r.params.get('type') === 'CONSENT_PURPOSE')
      .flush([
        { id: '1', type: 'CONSENT_PURPOSE', code: 'NEWSLETTER', label: 'Newsletter', active: true, sortOrder: 10, createdAt: '' },
      ]);

    expect(service.label('CONSENT_PURPOSE', 'NEWSLETTER')).toBe('Newsletter');
    expect(service.label('CONSENT_PURPOSE', 'UNKNOWN_CODE')).toBe('UNKNOWN_CODE');
  });

  it('returns empty string for a null/undefined code', () => {
    expect(service.label('GOAL_METRIC', null)).toBe('');
    expect(service.label('GOAL_METRIC', undefined)).toBe('');
  });

  it('allows a retry after a failed load', () => {
    service.ensureLoaded('INSIGHT_VERDICT');
    http.expectOne((r) => r.params.get('type') === 'INSIGHT_VERDICT').error(new ProgressEvent('fail'));

    // A second call retries because the failed type was removed from the requested set.
    service.ensureLoaded('INSIGHT_VERDICT');
    const retry = http.expectOne((r) => r.params.get('type') === 'INSIGHT_VERDICT');
    retry.flush([
      { id: '1', type: 'INSIGHT_VERDICT', code: 'CONVERTE', label: 'Converte (manter)', active: true, sortOrder: 10, createdAt: '' },
    ]);
    expect(service.label('INSIGHT_VERDICT', 'CONVERTE')).toBe('Converte (manter)');
  });
});
