import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { ApiError } from '../../core/http/api-error';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  DiscrepancyStatus,
  DiscrepancyView,
  EmployeeStatus,
  EmployeeView,
  JourneyView,
  TimeBankView,
} from './people.models';
import { PeopleService } from './people.service';

/**
 * People/HR screen (SPEC-0022, SPEC-0029 16d): collaborators, journey/time-bank read and the
 * discrepancy treatment queue. Lists/registers collaborators; reads a processed journey and the
 * time-bank of a collaborator/period; browses the discrepancy queue. Each data section uses
 * {@link ScreenState} for the loading/empty/error/permission states. Amounts are hours strings from
 * the backend (never client math). The payslip archive (multipart) stays with Compliance.
 */
@Component({
  selector: 'app-people-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './people-page.html',
})
export class PeoplePage implements OnInit, FormLeaveGuard {
  private readonly peopleService = inject(PeopleService);

  readonly statuses: EmployeeStatus[] = ['ACTIVE', 'ON_LEAVE', 'TERMINATED'];
  readonly discrepancyStatuses: DiscrepancyStatus[] = ['OPEN', 'RESOLVED'];

  // Collaborator list.
  filterStatus: EmployeeStatus | '' = '';
  readonly listState = signal<'loading' | 'success' | 'error'>('loading');
  readonly employees = signal<EmployeeView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly employeesState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'success') {
      return this.employees().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Register collaborator form.
  newIdentifier = '';
  newAdmissionDate = '';
  newContractedJourney = '';
  newContractDocumentId = '';
  readonly registerBusy = signal(false);
  readonly registerError = signal<string | null>(null);

  // Journey / time-bank read.
  journeyEmployeeId = '';
  journeyPeriod = '';
  readonly journey = signal<JourneyView | null>(null);
  readonly timebank = signal<TimeBankView | null>(null);
  readonly journeyError = signal<string | null>(null);

  // Discrepancy queue.
  discrepancyPeriod = '';
  discrepancyStatus: DiscrepancyStatus | '' = '';
  readonly discrepancyListState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly discrepancies = signal<DiscrepancyView[]>([]);
  readonly discrepancyError = signal<string | null>(null);

  readonly discrepanciesState = computed<ScreenStateKind>(() => {
    const s = this.discrepancyListState();
    if (s === 'idle') {
      return 'empty';
    }
    if (s === 'success') {
      return this.discrepancies().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  ngOnInit(): void {
    this.loadEmployees();
  }

  /** Whether the register form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return (
      !this.registerBusy() &&
      (!!this.newIdentifier || !!this.newAdmissionDate || !!this.newContractedJourney)
    );
  }

  /** (Re)loads the collaborator list using the current status filter. */
  loadEmployees(): void {
    this.listState.set('loading');
    this.peopleService.listEmployees(this.filterStatus).subscribe({
      next: (page) => {
        this.employees.set(page.content);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Registers a collaborator and reloads the list. */
  registerEmployee(): void {
    if (!this.newIdentifier || !this.newAdmissionDate || !this.newContractedJourney) {
      return;
    }
    this.registerBusy.set(true);
    this.registerError.set(null);
    this.peopleService
      .registerEmployee({
        identifier: this.newIdentifier,
        admissionDate: this.newAdmissionDate,
        contractedJourney: this.newContractedJourney,
        contractDocumentId: this.newContractDocumentId || null,
      })
      .subscribe({
        next: () => {
          this.registerBusy.set(false);
          this.newIdentifier = '';
          this.newAdmissionDate = '';
          this.newContractedJourney = '';
          this.newContractDocumentId = '';
          this.loadEmployees();
        },
        error: (error: ApiError) => {
          this.registerBusy.set(false);
          this.registerError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Reads the journey and time-bank for the entered collaborator/period. */
  loadJourney(): void {
    const id = this.journeyEmployeeId.trim();
    const period = this.journeyPeriod.trim();
    if (!id || !period) {
      return;
    }
    this.journeyError.set(null);
    this.journey.set(null);
    this.timebank.set(null);
    this.peopleService.journey(id, period).subscribe({
      next: (journey) => this.journey.set(journey),
      error: (error: ApiError) => this.journeyError.set(error?.code ?? 'error.internal'),
    });
    this.peopleService.timebank(id, period).subscribe({
      next: (timebank) => this.timebank.set(timebank),
      error: () => this.timebank.set(null),
    });
  }

  /** Browses the discrepancy queue using the current filters. */
  loadDiscrepancies(): void {
    this.discrepancyListState.set('loading');
    this.discrepancyError.set(null);
    this.peopleService
      .discrepancies(this.discrepancyPeriod.trim() || undefined, this.discrepancyStatus)
      .subscribe({
        next: (page) => {
          this.discrepancies.set(page.content);
          this.discrepancyListState.set('success');
        },
        error: (error: ApiError) => {
          this.discrepancyError.set(error?.code ?? 'error.internal');
          this.discrepancyListState.set('error');
        },
      });
  }

  /** PrimeNG Tag severity for a collaborator status. */
  statusSeverity(status: EmployeeStatus): 'success' | 'warn' | 'secondary' {
    if (status === 'ACTIVE') {
      return 'success';
    }
    return status === 'ON_LEAVE' ? 'warn' : 'secondary';
  }

  /** PrimeNG Tag severity for a discrepancy status. */
  discrepancySeverity(status: DiscrepancyStatus): 'warn' | 'success' {
    return status === 'OPEN' ? 'warn' : 'success';
  }
}
