import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EmployeeView } from './people.models';
import { PeopleService } from './people.service';

const EMPLOYEE: EmployeeView = {
  id: 'e1',
  identifier: 'EMP-001',
  admissionDate: '2026-01-10',
  contractedJourney: '44h',
  status: 'ACTIVE',
  contractDocumentId: null,
};

describe('PeopleService', () => {
  let http: HttpTestingController;
  let service: PeopleService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PeopleService],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PeopleService);
  });

  afterEach(() => http.verify());

  it('lists employees with a status filter', () => {
    service.listEmployees('ACTIVE').subscribe();
    const req = http.expectOne(
      (r) => r.url === '/api/people/employees' && r.params.get('status') === 'ACTIVE',
    );
    req.flush({ content: [EMPLOYEE], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('registers an employee (POST /employees)', () => {
    service
      .registerEmployee({ identifier: 'EMP-001', admissionDate: '2026-01-10', contractedJourney: '44h' })
      .subscribe();
    const req = http.expectOne('/api/people/employees');
    expect(req.request.method).toBe('POST');
    req.flush(EMPLOYEE);
  });

  it('reads the journey and time-bank for a period', () => {
    service.journey('e1', '2026-01').subscribe();
    http
      .expectOne((r) => r.url === '/api/people/employees/e1/journey' && r.params.get('period') === '2026-01')
      .flush({});

    service.timebank('e1', '2026-01').subscribe();
    http
      .expectOne((r) => r.url === '/api/people/employees/e1/timebank' && r.params.get('period') === '2026-01')
      .flush({});
  });

  it('processes a journey (POST)', () => {
    service
      .processJourney('e1', {
        period: '2026-01',
        sourceRef: 'REP-1',
        workedMinutes: 100,
        workingDays: 20,
        expectedPunches: 40,
        actualPunches: 40,
      })
      .subscribe();
    const req = http.expectOne('/api/people/employees/e1/journey');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('browses the discrepancy queue with filters', () => {
    service.discrepancies('2026-01', 'OPEN').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === '/api/people/discrepancies' &&
        r.params.get('period') === '2026-01' &&
        r.params.get('status') === 'OPEN',
    );
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
