import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CadastroService } from './cadastro.service';

describe('CadastroService', () => {
  let http: HttpTestingController;
  let service: CadastroService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CadastroService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CadastroService);
  });

  afterEach(() => http.verify());

  it('lists the cadastro types', () => {
    service.listTypes().subscribe();
    const req = http.expectOne('/api/cadastro/types');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('lists items of a type with the type param', () => {
    service.listItems('ASSET_TYPE').subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/cadastro/items' && r.params.get('type') === 'ASSET_TYPE',
    );
    req.flush([]);
  });

  it('creates an item (POST /items)', () => {
    service.create({ type: 'ASSET_TYPE', code: 'VEHICLE', label: 'Veículo', sortOrder: 40 }).subscribe();
    const req = http.expectOne('/api/cadastro/items');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('updates an item (PUT /items/{id})', () => {
    service.update('i1', { label: 'X', active: true, sortOrder: 1 }).subscribe();
    const req = http.expectOne('/api/cadastro/items/i1');
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('deactivates an item (DELETE /items/{id})', () => {
    service.deactivate('i1').subscribe();
    const req = http.expectOne('/api/cadastro/items/i1');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
