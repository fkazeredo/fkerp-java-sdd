import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { PageResponse } from '../../core/models/api.models';
import {
  CertificateView,
  JobRunView,
  ScheduledJobView,
  SystemAuditView,
} from './platform.models';
import { PlatformPage } from './platform-page';
import { PlatformService } from './platform.service';

const JOB: ScheduledJobView = {
  name: 'nightly-close',
  cron: '0 0 2 * * *',
  enabled: true,
  ownerModule: 'finance',
  lastRunAt: null,
};

const CERT: CertificateView = {
  subject: 'CN=Acme',
  holderDocument: '12.345.678/0001-00',
  fingerprint: 'AB:CD',
  validFrom: '2026-01-01',
  validUntil: '2027-01-01',
  daysToExpiry: 200,
  status: 'VALID',
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function base(overrides: Partial<PlatformService> = {}): Partial<PlatformService> {
  return {
    jobs: () => of([JOB]),
    runs: () => of(page<JobRunView>([])),
    certificateStatus: () => of(CERT),
    audit: () => of(page<SystemAuditView>([])),
    ...overrides,
  };
}

function configure(service: Partial<PlatformService>): void {
  TestBed.configureTestingModule({
    imports: [PlatformPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: PlatformService, useValue: service },
    ],
  });
}

describe('PlatformPage', () => {
  it('loads the job catalog and certificate (loading → success)', () => {
    configure(base());
    const fixture = TestBed.createComponent(PlatformPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.jobsListState()).toBe('success');
    expect(fixture.componentInstance.certScreenState()).toBe('success');
    expect(fixture.componentInstance.certificate()?.status).toBe('VALID');
  });

  it('shows the empty certificate state on a 404', () => {
    configure(
      base({
        certificateStatus: () =>
          throwError(() => ({ code: 'platform.certificate.notfound', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(PlatformPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.certScreenState()).toBe('empty');
  });

  it('renders the permission state on a 403 job catalog', () => {
    configure(
      base({ jobs: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })) }),
    );
    const fixture = TestBed.createComponent(PlatformPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.jobsError()).toBe('access.denied');
  });

  it('triggers a job and reloads the runs', () => {
    const trigger = vi.fn(() => of({} as JobRunView));
    const runs = vi.fn(() => of(page<JobRunView>([])));
    configure(base({ trigger, runs }));
    const fixture = TestBed.createComponent(PlatformPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.triggerJob(JOB);

    expect(trigger).toHaveBeenCalledWith('nightly-close');
    expect(runs).toHaveBeenCalledTimes(2);
  });

  it('surfaces a trigger 403 by its code', () => {
    configure(
      base({
        trigger: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(PlatformPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.triggerJob(JOB);

    expect(component.triggerError()).toBe('access.denied');
  });

  it('shows the empty audit state and error handling', () => {
    configure(
      base({
        audit: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
      }),
    );
    const fixture = TestBed.createComponent(PlatformPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.auditListState()).toBe('error');
  });

  it('maps job and certificate severities', () => {
    configure(base());
    const component = TestBed.createComponent(PlatformPage).componentInstance;

    expect(component.jobSeverity('SUCCEEDED')).toBe('success');
    expect(component.jobSeverity('RUNNING')).toBe('info');
    expect(component.jobSeverity('FAILED')).toBe('danger');
    expect(component.jobSeverity('SKIPPED')).toBe('secondary');
    expect(component.certSeverity('VALID')).toBe('success');
    expect(component.certSeverity('EXPIRING')).toBe('warn');
    expect(component.certSeverity('EXPIRED')).toBe('danger');
  });
});
