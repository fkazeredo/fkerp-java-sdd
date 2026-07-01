import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PointService } from './point.service';

describe('PointService', () => {
  let http: HttpTestingController;
  let service: PointService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PointService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PointService);
  });

  afterEach(() => http.verify());

  it('lists crawl runs with a status filter', () => {
    service.runs('DEAD_LETTER').subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/integration/point/runs' && r.params.get('status') === 'DEAD_LETTER',
    );
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('reads a snapshot by id', () => {
    service.snapshot('s1').subscribe();
    const req = http.expectOne('/api/integration/point/snapshots/s1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
