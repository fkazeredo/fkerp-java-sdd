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
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  AdminContractView,
  AdminExpenseKind,
  AdminExpenseView,
  AdminRecurrence,
  AdminSupplierStatus,
  AdminSupplierType,
  AdminSupplierView,
} from './admin.models';
import { AdminService } from './admin.service';

/**
 * Admin (back-office) screen (SPEC-0025, SPEC-0029 16d): administrative suppliers, their contracts and
 * recurring expenses. Lists/registers suppliers; lists a supplier's contracts and registers one;
 * registers a recurring expense (which posts the Finance ledger entry) and runs the contract-expiry
 * sweep. Writes require ROLE_FINANCE — a caller without it gets a 403 rendered as the permission state
 * by {@link ScreenState}. Amounts are shown in the original currency (never client math).
 */
@Component({
  selector: 'app-admin-page',
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
  templateUrl: './admin-page.html',
})
export class AdminPage implements OnInit, FormLeaveGuard {
  private readonly adminService = inject(AdminService);

  readonly types: AdminSupplierType[] = ['UTILITY', 'SOFTWARE', 'SERVICE', 'OTHER'];
  readonly statuses: AdminSupplierStatus[] = ['ACTIVE', 'INACTIVE'];
  readonly recurrences: AdminRecurrence[] = ['MONTHLY', 'YEARLY', 'OTHER'];
  readonly expenseKinds: AdminExpenseKind[] = ['UTILITY', 'AUTONOMOUS_SERVICE', 'SERVICE', 'OTHER'];
  readonly format = formatMoney;

  // Supplier list.
  filterType: AdminSupplierType | '' = '';
  filterStatus: AdminSupplierStatus | '' = '';
  readonly listState = signal<'loading' | 'success' | 'error'>('loading');
  readonly suppliers = signal<AdminSupplierView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly suppliersState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'success') {
      return this.suppliers().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Register supplier form.
  newType: AdminSupplierType = 'SERVICE';
  newIdentifier = '';
  newDisplayName = '';
  readonly registerBusy = signal(false);
  readonly registerError = signal<string | null>(null);

  // Contracts for a supplier.
  contractSupplierId = '';
  readonly contractsState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly contracts = signal<AdminContractView[]>([]);
  readonly contractsError = signal<string | null>(null);

  readonly contractsListState = computed<ScreenStateKind>(() => {
    const s = this.contractsState();
    if (s === 'idle') {
      return 'empty';
    }
    if (s === 'success') {
      return this.contracts().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Register contract form.
  newContractValidFrom = '';
  newContractValidUntil = '';
  newContractRecurrence: AdminRecurrence = 'MONTHLY';
  newContractAmount: number | null = null;
  newContractCurrency = 'BRL';
  readonly contractBusy = signal(false);
  readonly contractError = signal<string | null>(null);

  // Register expense form.
  expenseSupplierId = '';
  expensePeriod = '';
  expenseAmount: number | null = null;
  expenseCurrency = 'BRL';
  expenseKind: AdminExpenseKind = 'UTILITY';
  readonly expenseBusy = signal(false);
  readonly expenseError = signal<string | null>(null);
  readonly expense = signal<AdminExpenseView | null>(null);

  // Sweep.
  readonly sweepBusy = signal(false);
  readonly sweepResult = signal<number | null>(null);

  ngOnInit(): void {
    this.loadSuppliers();
  }

  /** Whether the register-supplier form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.registerBusy() && (!!this.newIdentifier || !!this.newDisplayName);
  }

  /** (Re)loads the supplier list using the current filters. */
  loadSuppliers(): void {
    this.listState.set('loading');
    this.adminService.listSuppliers(this.filterType, this.filterStatus).subscribe({
      next: (suppliers) => {
        this.suppliers.set(suppliers);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Registers a supplier and reloads the list. */
  registerSupplier(): void {
    if (!this.newIdentifier || !this.newDisplayName) {
      return;
    }
    this.registerBusy.set(true);
    this.registerError.set(null);
    this.adminService
      .registerSupplier({
        type: this.newType,
        identifier: this.newIdentifier,
        displayName: this.newDisplayName,
      })
      .subscribe({
        next: () => {
          this.registerBusy.set(false);
          this.newIdentifier = '';
          this.newDisplayName = '';
          this.loadSuppliers();
        },
        error: (error: ApiError) => {
          this.registerBusy.set(false);
          this.registerError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Loads a supplier's contracts. */
  loadContracts(): void {
    const id = this.contractSupplierId.trim();
    if (!id) {
      return;
    }
    this.contractsState.set('loading');
    this.contractsError.set(null);
    this.adminService.contracts(id).subscribe({
      next: (contracts) => {
        this.contracts.set(contracts);
        this.contractsState.set('success');
      },
      error: (error: ApiError) => {
        this.contractsError.set(error?.code ?? 'error.internal');
        this.contractsState.set('error');
      },
    });
  }

  /** Registers a contract for the entered supplier and reloads the contracts. */
  registerContract(): void {
    const id = this.contractSupplierId.trim();
    if (!id || !this.newContractValidFrom) {
      return;
    }
    this.contractBusy.set(true);
    this.contractError.set(null);
    const amount =
      this.newContractAmount != null
        ? { amount: this.newContractAmount, currency: this.newContractCurrency }
        : null;
    this.adminService
      .registerContract(id, {
        validFrom: this.newContractValidFrom,
        validUntil: this.newContractValidUntil || null,
        recurrence: this.newContractRecurrence,
        amount,
      })
      .subscribe({
        next: () => {
          this.contractBusy.set(false);
          this.newContractValidUntil = '';
          this.newContractAmount = null;
          this.loadContracts();
        },
        error: (error: ApiError) => {
          this.contractBusy.set(false);
          this.contractError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Registers a recurring expense (posts the Finance ledger entry). */
  registerExpense(): void {
    const id = this.expenseSupplierId.trim();
    if (!id || !this.expensePeriod || this.expenseAmount == null) {
      return;
    }
    this.expenseBusy.set(true);
    this.expenseError.set(null);
    this.expense.set(null);
    this.adminService
      .registerExpense({
        supplierId: id,
        period: this.expensePeriod,
        amount: { amount: this.expenseAmount, currency: this.expenseCurrency },
        kind: this.expenseKind,
      })
      .subscribe({
        next: (expense) => {
          this.expenseBusy.set(false);
          this.expense.set(expense);
        },
        error: (error: ApiError) => {
          this.expenseBusy.set(false);
          this.expenseError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Runs the contract-expiry sweep and shows how many were flagged. */
  runSweep(): void {
    this.sweepBusy.set(true);
    this.sweepResult.set(null);
    this.adminService.flagExpiring().subscribe({
      next: (result) => {
        this.sweepBusy.set(false);
        this.sweepResult.set(result.flagged);
      },
      error: (error: ApiError) => {
        this.sweepBusy.set(false);
        this.errorCode.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** PrimeNG Tag severity for a supplier status. */
  statusSeverity(status: AdminSupplierStatus): 'success' | 'secondary' {
    return status === 'ACTIVE' ? 'success' : 'secondary';
  }
}
