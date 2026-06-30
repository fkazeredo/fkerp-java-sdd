import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { Money, formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { CaseStatus, ReconciliationCaseView } from './reconciliation.models';
import { ReconciliationService } from './reconciliation.service';

/**
 * Reconciliation screen (SPEC-0007, repaginated SPEC-0026): the cases prioritized by discrepancy,
 * with the loading/empty/error/permission states (BR8) via {@link ScreenState}, and a settlement
 * form on the selected case that records the realized values and shows the derived realized spread,
 * FX gain/loss and discrepancy.
 */
@Component({
  selector: 'app-reconciliation-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    MessageModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './reconciliation-page.html',
})
export class ReconciliationPage implements OnInit {
  private readonly reconciliationService = inject(ReconciliationService);

  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly cases = signal<ReconciliationCaseView[]>([]);
  readonly errorCode = signal<string | null>(null);
  readonly selected = signal<ReconciliationCaseView | null>(null);
  readonly settleError = signal<string | null>(null);
  readonly busy = signal(false);

  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.cases().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  readonly format = formatMoney;

  received: number | null = null;
  rate: number | null = null;
  supplierPaid: number | null = null;
  commissionReceived: number | null = null;
  commissionPaid: number | null = null;

  ngOnInit(): void {
    this.load();
  }

  /** Loads the cases (ordered by discrepancy). */
  load(): void {
    this.state.set('loading');
    this.reconciliationService.list().subscribe({
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

  /** Selects a case to settle. */
  select(reconciliationCase: ReconciliationCaseView): void {
    this.selected.set(reconciliationCase);
    this.settleError.set(null);
  }

  /** Records the settlement for the selected case. */
  settle(): void {
    const current = this.selected();
    if (!current) {
      return;
    }
    const currency = current.expectedSpread.currency;
    const money = (value: number | null): Money | null =>
      value == null ? null : { amount: value, currency };

    this.busy.set(true);
    this.settleError.set(null);
    this.reconciliationService
      .settle(current.caseId, {
        amountReceivedFromAgency: money(this.received),
        supplierSettlementRate: this.rate,
        supplierPaidAmount: money(this.supplierPaid),
        commissionReceivedFromSupplier: money(this.commissionReceived),
        commissionPaidToAgent: money(this.commissionPaid),
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.selected.set(updated);
          this.load();
        },
        error: (error: ApiError) => {
          this.busy.set(false);
          this.settleError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** PrimeNG Tag severity for a reconciliation case status. */
  statusSeverity(status: CaseStatus): 'success' | 'danger' | 'warn' | 'info' {
    switch (status) {
      case 'SETTLED':
        return 'success';
      case 'DISCREPANCY':
        return 'danger';
      case 'PARTIALLY_SETTLED':
        return 'warn';
      default:
        return 'info';
    }
  }
}
