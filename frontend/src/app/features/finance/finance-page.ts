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
  EntryStatus,
  EntryType,
  LedgerDirection,
  LedgerEntryView,
  PartyType,
  PeriodStatus,
  PeriodView,
  TrialBalanceView,
} from './finance.models';
import { FinanceService } from './finance.service';

/**
 * Finance screen (SPEC-0015, SPEC-0029 16a): the AP/AR ledger filtered by direction/status/period/
 * party, a period lookup with its per-currency trial balance (DL-0013), a new-entry form and the
 * monthly close (which fails with `finance.period.cannot-close` when the Compliance vetoes it). Every
 * data section uses {@link ScreenState} for the loading/empty/error/permission states.
 */
@Component({
  selector: 'app-finance-page',
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
  templateUrl: './finance-page.html',
})
export class FinancePage implements OnInit, FormLeaveGuard {
  private readonly financeService = inject(FinanceService);

  readonly directions: LedgerDirection[] = ['PAYABLE', 'RECEIVABLE'];
  readonly statuses: EntryStatus[] = ['PROVISIONAL', 'CONFIRMED', 'SETTLED'];
  readonly partyTypes: PartyType[] = ['AGENCY', 'AGENT', 'SUPPLIER', 'OTHER'];
  readonly entryTypes: EntryType[] = [
    'COMMISSION_RECEIVABLE',
    'COMMISSION_PAYABLE',
    'PENALTY',
    'UTILITY_EXPENSE',
    'AUTONOMOUS_SERVICE',
    'SUPPLIER_SETTLEMENT',
    'REFUND',
    'TAX_PAYABLE',
    'SERVICE',
    'OTHER_EXPENSE',
  ];

  readonly format = formatMoney;

  // Ledger list state.
  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly entries = signal<LedgerEntryView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.entries().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Ledger filters.
  filterDirection: LedgerDirection | '' = '';
  filterStatus: EntryStatus | '' = '';
  filterPeriod = '';
  filterParty = '';

  // New-entry form.
  newDirection: LedgerDirection = 'PAYABLE';
  newPartyId = '';
  newPartyType: PartyType = 'SUPPLIER';
  newAmount: number | null = null;
  newCurrency = 'BRL';
  newEntryType: EntryType = 'COMMISSION_PAYABLE';
  newPeriod = '';
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  // Period lookup state.
  periodQuery = '';
  readonly period = signal<PeriodView | null>(null);
  readonly trialBalance = signal<TrialBalanceView | null>(null);
  readonly periodState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly periodError = signal<string | null>(null);
  readonly closing = signal(false);
  readonly closeError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadEntries();
  }

  /** Whether the new-entry form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.submitting() && (!!this.newPartyId || this.newAmount != null);
  }

  /** (Re)loads the ledger using the current filters. */
  loadEntries(): void {
    this.state.set('loading');
    this.financeService
      .listEntries({
        direction: this.filterDirection,
        status: this.filterStatus,
        period: this.filterPeriod,
        party: this.filterParty,
      })
      .subscribe({
        next: (page) => {
          this.entries.set(page.content);
          this.state.set('success');
        },
        error: (error: ApiError) => {
          this.errorCode.set(error?.code ?? 'error.internal');
          this.state.set('error');
        },
      });
  }

  /** Submits the new-entry form; on success resets amount and reloads the ledger. */
  submit(): void {
    if (this.newAmount == null || !this.newPartyId || !this.newPeriod) {
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);
    this.financeService
      .createEntry({
        direction: this.newDirection,
        party: { id: this.newPartyId, type: this.newPartyType },
        amount: { amount: this.newAmount, currency: this.newCurrency },
        entryType: this.newEntryType,
        period: this.newPeriod,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.newAmount = null;
          this.newPartyId = '';
          this.loadEntries();
        },
        error: (error: ApiError) => {
          this.submitting.set(false);
          this.submitError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Looks up a period and its trial balance. */
  lookupPeriod(): void {
    const yyyymm = this.periodQuery.trim();
    if (!yyyymm) {
      return;
    }
    this.periodState.set('loading');
    this.periodError.set(null);
    this.closeError.set(null);
    this.financeService.getPeriod(yyyymm).subscribe({
      next: (period) => {
        this.period.set(period);
        this.financeService.trialBalance(yyyymm).subscribe({
          next: (tb) => {
            this.trialBalance.set(tb);
            this.periodState.set('success');
          },
          error: (error: ApiError) => {
            this.periodError.set(error?.code ?? 'error.internal');
            this.periodState.set('error');
          },
        });
      },
      error: (error: ApiError) => {
        this.periodError.set(error?.code ?? 'error.internal');
        this.periodState.set('error');
      },
    });
  }

  /** Closes the currently looked-up period (may be vetoed by Compliance). */
  closePeriod(): void {
    const current = this.period();
    if (!current) {
      return;
    }
    this.closing.set(true);
    this.closeError.set(null);
    this.financeService.closePeriod(current.period).subscribe({
      next: (period) => {
        this.closing.set(false);
        this.period.set(period);
        this.periodQuery = period.period;
        this.lookupPeriod();
      },
      error: (error: ApiError) => {
        this.closing.set(false);
        this.closeError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** PrimeNG Tag severity for an entry status. */
  statusSeverity(status: EntryStatus): 'success' | 'info' | 'secondary' {
    switch (status) {
      case 'SETTLED':
        return 'success';
      case 'CONFIRMED':
        return 'info';
      default:
        return 'secondary';
    }
  }

  /** PrimeNG Tag severity for a period status. */
  periodSeverity(status: PeriodStatus): 'success' | 'warn' | 'info' {
    switch (status) {
      case 'CLOSED':
        return 'success';
      case 'CLOSING':
        return 'warn';
      default:
        return 'info';
    }
  }
}
