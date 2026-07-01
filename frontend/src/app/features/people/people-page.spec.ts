import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PageResponse } from '../../core/models/api.models';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { DiscrepancyView, EmployeeView, JourneyView, TimeBankView } from './people.models';
import { PeoplePage } from './people-page';
import { PeopleService } from './people.service';

const EMPLOYEE: EmployeeView = {
  id: 'e1',
  identifier: 'EMP-001',
  admissionDate: '2026-01-10',
  contractedJourney: '44h',
  status: 'ACTIVE',
  contractDocumentId: null,
};

const JOURNEY: JourneyView = {
  employeeId: 'e1',
  period: '2026-01',
  workedHours: '176h',
  contractedHours: '176h',
  balance: '0h',
  snapshotRef: null,
  processedAt: '2026-02-01T00:00:00Z',
};

const TIMEBANK: TimeBankView = {
  period: '2026-01',
  workedHours: '176h',
  contractedHours: '176h',
  balance: '+2h',
  discrepancies: 1,
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function configure(service: Partial<PeopleService>): void {
  TestBed.configureTestingModule({
    imports: [PeoplePage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: PeopleService, useValue: service },
    ],
  });
}

describe('PeoplePage', () => {
  it('loads the employee list (loading → success)', () => {
    configure({ listEmployees: () => of(page([EMPLOYEE])) });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.employeesState()).toBe('success');
    expect(fixture.componentInstance.employees().length).toBe(1);
  });

  it('shows the empty state when there are no employees', () => {
    configure({ listEmployees: () => of(page<EmployeeView>([])) });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.employeesState()).toBe('empty');
  });

  it('shows the error state when the employee list fails', () => {
    configure({
      listEmployees: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.employeesState()).toBe('error');
  });

  it('renders the permission state on a 403 employee list', () => {
    configure({
      listEmployees: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('registers an employee and reloads the list', () => {
    const registerEmployee = vi.fn(() => of(EMPLOYEE));
    const listEmployees = vi.fn(() => of(page([EMPLOYEE])));
    configure({ listEmployees, registerEmployee });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newIdentifier = 'EMP-001';
    component.newAdmissionDate = '2026-01-10';
    component.newContractedJourney = '44h';
    expect(component.isDirty()).toBe(true);
    component.registerEmployee();

    expect(registerEmployee).toHaveBeenCalled();
    expect(listEmployees).toHaveBeenCalledTimes(2);
    expect(component.isDirty()).toBe(false);
  });

  it('reads the journey and time-bank for a collaborator/period', () => {
    configure({
      listEmployees: () => of(page<EmployeeView>([])),
      journey: () => of(JOURNEY),
      timebank: () => of(TIMEBANK),
    });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.journeyEmployeeId = 'e1';
    component.journeyPeriod = '2026-01';
    component.loadJourney();

    expect(component.journey()?.balance).toBe('0h');
    expect(component.timebank()?.discrepancies).toBe(1);
  });

  it('surfaces a journey error by its code', () => {
    configure({
      listEmployees: () => of(page<EmployeeView>([])),
      journey: () => throwError(() => ({ code: 'people.journey.invalid', message: '', fields: [] })),
      timebank: () => throwError(() => ({ code: 'x', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.journeyEmployeeId = 'e1';
    component.journeyPeriod = '2026-01';
    component.loadJourney();

    expect(component.journeyError()).toBe('people.journey.invalid');
  });

  it('browses the discrepancy queue (empty)', () => {
    configure({
      listEmployees: () => of(page<EmployeeView>([])),
      discrepancies: () => of(page<DiscrepancyView>([])),
    });
    const fixture = TestBed.createComponent(PeoplePage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.discrepanciesState()).toBe('empty');
    component.loadDiscrepancies();
    expect(component.discrepanciesState()).toBe('empty');
  });

  it('maps status and discrepancy severities', () => {
    configure({ listEmployees: () => of(page<EmployeeView>([])) });
    const component = TestBed.createComponent(PeoplePage).componentInstance;

    expect(component.statusSeverity('ACTIVE')).toBe('success');
    expect(component.statusSeverity('ON_LEAVE')).toBe('warn');
    expect(component.statusSeverity('TERMINATED')).toBe('secondary');
    expect(component.discrepancySeverity('OPEN')).toBe('warn');
    expect(component.discrepancySeverity('RESOLVED')).toBe('success');
  });
});
