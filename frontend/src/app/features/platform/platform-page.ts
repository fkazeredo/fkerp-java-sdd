import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  AuditType,
  CertificateStatus,
  CertificateView,
  JobRunView,
  JobStatus,
  ScheduledJobView,
  SystemAuditView,
} from './platform.models';
import { PlatformService } from './platform.service';

/**
 * Platform/TI screen (SPEC-0023, SPEC-0029 16d): governed jobs (catalog + run history + manual
 * trigger), the e-CNPJ certificate STATUS (metadata only — the key/password are never shown) and the
 * consolidated system audit. Job trigger and certificate import require ROLE_IT — the backend is the
 * authority, so a 403 renders as the permission state. Each data section uses {@link ScreenState}.
 */
@Component({
  selector: 'app-platform-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './platform-page.html',
})
export class PlatformPage implements OnInit {
  private readonly platformService = inject(PlatformService);

  readonly jobStatuses: JobStatus[] = ['RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'];
  readonly auditTypes: AuditType[] = [
    'JOB_RUN_STARTED',
    'JOB_RUN_FINISHED',
    'CERTIFICATE_EXPIRING',
    'CERTIFICATE_CUSTODIED',
    'SECURITY_EVENT',
    'AUTH_LOGIN',
    'ACCESS_DENIED',
    'INTEGRATION_EVENT',
    'ADMIN_CHANGE',
  ];

  // Job catalog.
  readonly jobsState = signal<'loading' | 'success' | 'error'>('loading');
  readonly jobs = signal<ScheduledJobView[]>([]);
  readonly jobsError = signal<string | null>(null);

  readonly jobsListState = computed<ScreenStateKind>(() => {
    const s = this.jobsState();
    if (s === 'success') {
      return this.jobs().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  readonly triggerBusy = signal<string | null>(null);
  readonly triggerError = signal<string | null>(null);

  // Run history.
  runFilterJob = '';
  runFilterStatus: JobStatus | '' = '';
  readonly runsState = signal<'loading' | 'success' | 'error'>('loading');
  readonly runs = signal<JobRunView[]>([]);
  readonly runsError = signal<string | null>(null);

  readonly runsListState = computed<ScreenStateKind>(() => {
    const s = this.runsState();
    if (s === 'success') {
      return this.runs().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Certificate status (metadata only).
  readonly certState = signal<'loading' | 'success' | 'error'>('loading');
  readonly certificate = signal<CertificateView | null>(null);
  readonly certError = signal<string | null>(null);

  readonly certScreenState = computed<ScreenStateKind>(() => {
    const s = this.certState();
    if (s === 'success') {
      return this.certificate() ? 'success' : 'empty';
    }
    return s;
  });

  // System audit.
  auditActor = '';
  auditType: AuditType | '' = '';
  readonly auditState = signal<'loading' | 'success' | 'error'>('loading');
  readonly audit = signal<SystemAuditView[]>([]);
  readonly auditError = signal<string | null>(null);

  readonly auditListState = computed<ScreenStateKind>(() => {
    const s = this.auditState();
    if (s === 'success') {
      return this.audit().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  ngOnInit(): void {
    this.loadJobs();
    this.loadRuns();
    this.loadCertificate();
    this.loadAudit();
  }

  /** (Re)loads the governed job catalog. */
  loadJobs(): void {
    this.jobsState.set('loading');
    this.platformService.jobs().subscribe({
      next: (jobs) => {
        this.jobs.set(jobs);
        this.jobsState.set('success');
      },
      error: (error: ApiError) => {
        this.jobsError.set(error?.code ?? 'error.internal');
        this.jobsState.set('error');
      },
    });
  }

  /** Manually triggers a governed job (ROLE_IT) and reloads the run history. */
  triggerJob(job: ScheduledJobView): void {
    this.triggerBusy.set(job.name);
    this.triggerError.set(null);
    this.platformService.trigger(job.name).subscribe({
      next: () => {
        this.triggerBusy.set(null);
        this.loadRuns();
      },
      error: (error: ApiError) => {
        this.triggerBusy.set(null);
        this.triggerError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** (Re)loads the run history using the current filters. */
  loadRuns(): void {
    this.runsState.set('loading');
    this.platformService.runs(this.runFilterJob.trim() || undefined, this.runFilterStatus).subscribe({
      next: (page) => {
        this.runs.set(page.content);
        this.runsState.set('success');
      },
      error: (error: ApiError) => {
        this.runsError.set(error?.code ?? 'error.internal');
        this.runsState.set('error');
      },
    });
  }

  /** Loads the certificate status (metadata only). A 404 is shown as the empty state. */
  loadCertificate(): void {
    this.certState.set('loading');
    this.platformService.certificateStatus().subscribe({
      next: (certificate) => {
        this.certificate.set(certificate);
        this.certState.set('success');
      },
      error: (error: ApiError) => {
        if (error?.code === 'platform.certificate.notfound' || error?.code === 'error.notfound') {
          this.certificate.set(null);
          this.certState.set('success');
          return;
        }
        this.certError.set(error?.code ?? 'error.internal');
        this.certState.set('error');
      },
    });
  }

  /** (Re)loads the system-audit trail using the current filters. */
  loadAudit(): void {
    this.auditState.set('loading');
    this.platformService.audit(this.auditActor.trim() || undefined, this.auditType).subscribe({
      next: (page) => {
        this.audit.set(page.content);
        this.auditState.set('success');
      },
      error: (error: ApiError) => {
        this.auditError.set(error?.code ?? 'error.internal');
        this.auditState.set('error');
      },
    });
  }

  /** PrimeNG Tag severity for a job status. */
  jobSeverity(status: JobStatus): 'success' | 'info' | 'danger' | 'secondary' {
    switch (status) {
      case 'SUCCEEDED':
        return 'success';
      case 'RUNNING':
        return 'info';
      case 'FAILED':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  /** PrimeNG Tag severity for a certificate status. */
  certSeverity(status: CertificateStatus): 'success' | 'warn' | 'danger' {
    if (status === 'VALID') {
      return 'success';
    }
    return status === 'EXPIRING' ? 'warn' : 'danger';
  }
}
