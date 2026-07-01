import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { IdentityService } from './identity.service';

describe('IdentityService', () => {
  let http: HttpTestingController;
  let service: IdentityService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), IdentityService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(IdentityService);
  });

  afterEach(() => http.verify());

  it('reads the role catalogue', () => {
    service.roles().subscribe();
    const req = http.expectOne('/api/identity/roles');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('reads the access-audit trail with filters', () => {
    service.accessAudit('director', 'ACCESS_DENIED').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/identity/access-audit' &&
        r.params.get('actor') === 'director' &&
        r.params.get('type') === 'ACCESS_DENIED',
    );
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
