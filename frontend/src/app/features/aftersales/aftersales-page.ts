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
import { CadastroLabelPipe } from '../../core/cadastro/cadastro-label.pipe';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  CaseResolution,
  SupportCaseStatus,
  SupportCaseType,
  SupportCaseView,
} from './aftersales.models';
import { AfterSalesService } from './aftersales.service';

/**
 * AfterSales screen (SPEC-0018, SPEC-0029 16b): lists support cases filtered by type/status/booking/
 * SLA-breach, opens a new case, drives its state machine (assign/progress/wait/close) and resolves it
 * (a REFUND_APPROVED triggers a Payout REFUND; a CANCEL_APPROVED triggers a Booking cancellation —
 * BR2/BR3). The `breached` flag is an orthogonal SLA alert that never blocks the workflow (BR4). Each
 * data section uses {@link ScreenState} for the loading/empty/error/permission states.
 */
@Component({
  selector: 'app-aftersales-page',
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
    CadastroLabelPipe,
  ],
  templateUrl: './aftersales-page.html',
})
export class AfterSalesPage implements OnInit, FormLeaveGuard {
  private readonly afterSalesService = inject(AfterSalesService);

  readonly types: SupportCaseType[] = [
    'COMPLAINT',
    'CHANGE_REQUEST',
    'CANCELLATION_REQUEST',
    'REFUND_REQUEST',
    'INFO',
  ];
  readonly statuses: SupportCaseStatus[] = [
    'OPEN',
    'IN_PROGRESS',
    'WAITING',
    'RESOLVED',
    'CLOSED',
  ];
  readonly resolutions: CaseResolution[] = [
    'REFUND_APPROVED',
    'CANCEL_APPROVED',
    'RESOLVED_NO_ACTION',
    'REJECTED',
  ];
  readonly breachedOptions = [
    { label: 'aftersales.filterAll', value: null },
    { label: 'aftersales.breachedOnly', value: true },
    { label: 'aftersales.notBreached', value: false },
  ];

  readonly format = formatMoney;

  // List state.
  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly cases = signal<SupportCaseView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.cases().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Filters.
  filterType: SupportCaseType | '' = '';
  filterStatus: SupportCaseStatus | '' = '';
  filterBooking = '';
  filterBreached: boolean | null = null;

  // Open-case form.
  newBookingId = '';
  newType: SupportCaseType = 'COMPLAINT';
  newSummary = '';
  readonly opening = signal(false);
  readonly openError = signal<string | null>(null);

  // Selection / detail + actions.
  readonly selected = signal<SupportCaseView | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly busy = signal(false);

  // Resolve form.
  resolution: CaseResolution = 'RESOLVED_NO_ACTION';
  resolveAmount: number | null = null;
  resolveCurrency = 'BRL';
  resolveReason = '';

  ngOnInit(): void {
    this.load();
  }

  /** Whether the open-case form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.opening() && (!!this.newBookingId || !!this.newSummary);
  }

  /** (Re)loads the case list using the current filters. */
  load(): void {
    this.state.set('loading');
    this.afterSalesService
      .list({
        type: this.filterType,
        status: this.filterStatus,
        bookingId: this.filterBooking,
        breached: this.filterBreached,
      })
      .subscribe({
        next: (page) => {
          this.cases.set(page.content);
          this.state.set('success');
        },
        error: (error: ApiError) => {
          this.errorCode.set(error?.code ?? 'error.internal');
          this.state.set('error');
        },
      });
  }

  /** Selects a case for the detail/action panel. */
  select(supportCase: SupportCaseView): void {
    this.selected.set(supportCase);
    this.actionError.set(null);
  }

  /** Opens a new case, selects it and reloads the list. */
  open(): void {
    if (!this.newBookingId) {
      return;
    }
    this.opening.set(true);
    this.openError.set(null);
    this.afterSalesService
      .open({ bookingId: this.newBookingId, type: this.newType, summary: this.newSummary || null })
      .subscribe({
        next: (opened) => {
          this.opening.set(false);
          this.newBookingId = '';
          this.newSummary = '';
          this.selected.set(opened);
          this.load();
        },
        error: (error: ApiError) => {
          this.opening.set(false);
          this.openError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Drives a lifecycle transition on the selected case. */
  transition(action: 'assign' | 'progress' | 'wait' | 'close'): void {
    const current = this.selected();
    if (!current) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.afterSalesService.transition(current.id, action).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.selected.set(updated);
        this.load();
      },
      error: (error: ApiError) => {
        this.busy.set(false);
        this.actionError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** Resolves the selected case (may trigger a refund/cancellation side effect). */
  resolve(): void {
    const current = this.selected();
    if (!current) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    const amount =
      this.resolveAmount != null
        ? { amount: this.resolveAmount, currency: this.resolveCurrency }
        : null;
    this.afterSalesService
      .resolve(current.id, {
        resolution: this.resolution,
        amount,
        cancellationReason: this.resolveReason || null,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.resolveAmount = null;
          this.resolveReason = '';
          this.selected.set(updated);
          this.load();
        },
        error: (error: ApiError) => {
          this.busy.set(false);
          this.actionError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** PrimeNG Tag severity for a case status. */
  statusSeverity(status: SupportCaseStatus): 'success' | 'info' | 'warn' | 'secondary' {
    switch (status) {
      case 'RESOLVED':
      case 'CLOSED':
        return 'success';
      case 'IN_PROGRESS':
        return 'info';
      case 'WAITING':
        return 'warn';
      default:
        return 'secondary';
    }
  }
}
