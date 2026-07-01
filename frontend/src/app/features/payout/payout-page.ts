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
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  PayeeType,
  PaymentOutcome,
  PayoutKind,
  PayoutStatus,
  PayoutView,
} from './payout.models';
import { PayoutService } from './payout.service';

/**
 * Payout screen (SPEC-0017, SPEC-0029 16a): lists repasses/settlements/refunds filtered by kind/
 * status/payee, opens one with its installments, creates a new one (repass/settlement with rate/
 * refund with originRef) and executes it. A provider failure lands an explicit FAILED, never a false
 * EXECUTED (BR2). Each data section uses {@link ScreenState}.
 */
@Component({
  selector: 'app-payout-page',
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
  templateUrl: './payout-page.html',
})
export class PayoutPage implements OnInit, FormLeaveGuard {
  private readonly payoutService = inject(PayoutService);

  readonly kinds: PayoutKind[] = ['AGENT_COMMISSION', 'SUPPLIER_SETTLEMENT', 'REFUND'];
  readonly statuses: PayoutStatus[] = ['PENDING', 'EXECUTING', 'EXECUTED', 'FAILED'];
  readonly payeeTypes: PayeeType[] = ['AGENT', 'SUPPLIER', 'CUSTOMER'];
  readonly outcomes: PaymentOutcome[] = ['SUCCEEDED', 'FAILED'];

  readonly format = formatMoney;

  // List state.
  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly payouts = signal<PayoutView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.payouts().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Filters.
  filterKind: PayoutKind | '' = '';
  filterStatus: PayoutStatus | '' = '';
  filterPayee = '';

  // Selection / detail.
  readonly selected = signal<PayoutView | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly busy = signal(false);
  outcomeHint: PaymentOutcome = 'SUCCEEDED';

  // Create form.
  newKind: PayoutKind = 'AGENT_COMMISSION';
  newPayeeId = '';
  newPayeeType: PayeeType = 'AGENT';
  newAmount: number | null = null;
  newCurrency = 'BRL';
  newSettlementRate: number | null = null;
  newOriginRef = '';
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  /** Whether the create form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.creating() && (!!this.newPayeeId || this.newAmount != null);
  }

  /** (Re)loads the payouts using the current filters. */
  load(): void {
    this.state.set('loading');
    this.payoutService
      .list({ kind: this.filterKind, status: this.filterStatus, payee: this.filterPayee })
      .subscribe({
        next: (page) => {
          this.payouts.set(page.content);
          this.state.set('success');
        },
        error: (error: ApiError) => {
          this.errorCode.set(error?.code ?? 'error.internal');
          this.state.set('error');
        },
      });
  }

  /** Selects a payout for the detail panel. */
  select(payout: PayoutView): void {
    this.selected.set(payout);
    this.actionError.set(null);
  }

  /** Creates a payout and selects it. */
  create(): void {
    if (this.newAmount == null || !this.newPayeeId) {
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    this.payoutService
      .create({
        kind: this.newKind,
        payee: { id: this.newPayeeId, type: this.newPayeeType },
        amount: { amount: this.newAmount, currency: this.newCurrency },
        settlementRate: this.newSettlementRate,
        originRef: this.newOriginRef || null,
      })
      .subscribe({
        next: (payout) => {
          this.creating.set(false);
          this.newAmount = null;
          this.newPayeeId = '';
          this.newOriginRef = '';
          this.selected.set(payout);
          this.load();
        },
        error: (error: ApiError) => {
          this.creating.set(false);
          this.createError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Executes the selected payout (or its next installment). */
  execute(): void {
    const current = this.selected();
    if (!current) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.payoutService.execute(current.id, { outcomeHint: this.outcomeHint }).subscribe({
      next: (payout) => {
        this.busy.set(false);
        this.selected.set(payout);
        this.load();
      },
      error: (error: ApiError) => {
        this.busy.set(false);
        this.actionError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** PrimeNG Tag severity for a payout / installment status. */
  statusSeverity(status: PayoutStatus): 'success' | 'danger' | 'warn' | 'secondary' {
    switch (status) {
      case 'EXECUTED':
        return 'success';
      case 'FAILED':
        return 'danger';
      case 'EXECUTING':
        return 'warn';
      default:
        return 'secondary';
    }
  }
}
