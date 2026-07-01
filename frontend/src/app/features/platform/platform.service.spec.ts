import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlatformService } from './platform.service';

describe('PlatformService', () => {
  let http: HttpTestingController;
  let service: PlatformService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PlatformService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PlatformService);
  });

  afterEach(() => http.verify());

  it('reads the job catalog', () => {
    service.jobs().subscribe();
    const req = http.expectOne('/api/platform/jobs');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('lists runs with job/status filters', () => {
    service.runs('nightly', 'FAILED').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/platform/jobs/runs' &&
        r.params.get('job') === 'nightly' &&
        r.params.get('status') === 'FAILED',
    );
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('triggers a job by name (POST /trigger)', () => {
    service.trigger('nightly close').subscribe();
    const req = http.expectOne('/api/platform/jobs/nightly%20close/trigger');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('reads the certificate status (metadata only)', () => {
    service.certificateStatus().subscribe();
    const req = http.expectOne('/api/platform/certificate/status');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('reads the system audit with filters', () => {
    service.audit('director', 'AUTH_LOGIN').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/platform/audit' &&
        r.params.get('actor') === 'director' &&
        r.params.get('type') === 'AUTH_LOGIN',
    );
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
