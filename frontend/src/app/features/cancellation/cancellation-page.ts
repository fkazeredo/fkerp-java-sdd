import { Component, inject, signal } from '@angular/core';
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
import { ScreenState } from '../../shared/screen-state/screen-state';
import {
  CancellationPolicyView,
  CancellationType,
  CostBearer,
  PenaltyWindow,
} from './cancellation.models';
import { CancellationService } from './cancellation.service';

/**
 * Cancellation policy admin screen (SPEC-0010, SPEC-0029 16b): reads and administers the
 * cancellation/no-show policy per product/supplier scope. It surfaces the merchant trap
 * (ALL_SALES_FINAL ⇒ not refundable to the supplier — BR3/BR5) and who bears the penalty
 * (AGENCY/ACME/SUPPLIER — BR5/BR8), plus the penalty windows (hoursBefore × penaltyPct — BR2) and
 * the no-show fee. The lookup section uses {@link ScreenState}. Implements {@link FormLeaveGuard}.
 */
@Component({
  selector: 'app-cancellation-page',
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
  templateUrl: './cancellation-page.html',
})
export class CancellationPage implements FormLeaveGuard {
  private readonly cancellationService = inject(CancellationService);

  readonly types: CancellationType[] = ['STANDARD', 'ALL_SALES_FINAL', 'CUSTOM'];
  readonly costBearers: CostBearer[] = ['AGENCY', 'ACME', 'SUPPLIER'];

  readonly format = formatMoney;

  // Lookup state.
  scopeRef = '';
  readonly policy = signal<CancellationPolicyView | null>(null);
  readonly state = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly errorCode = signal<string | null>(null);

  // Edit form.
  editType: CancellationType = 'STANDARD';
  editRefundable = true;
  editCostBearer: CostBearer = 'AGENCY';
  editMerchantOfRecord = false;
  editNoShowFee: number | null = null;
  editNoShowCurrency = 'BRL';
  editWaivedIfFlightCancelled = false;
  windows = signal<PenaltyWindow[]>([]);
  newWindowHours: number | null = null;
  newWindowPct: number | null = null;
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  private dirty = false;

  /** Whether the edit form holds unsaved changes (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.saving() && this.dirty;
  }

  /** Marks the edit form as touched. */
  markDirty(): void {
    this.dirty = true;
  }

  /** Reads the policy for the given scope reference and seeds the edit form. */
  lookup(): void {
    const ref = this.scopeRef.trim();
    if (!ref) {
      return;
    }
    this.state.set('loading');
    this.errorCode.set(null);
    this.saveError.set(null);
    this.cancellationService.get(ref).subscribe({
      next: (view) => {
        this.policy.set(view);
        this.seedForm(view);
        this.state.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }

  /** Adds a penalty window to the working set. */
  addWindow(): void {
    if (this.newWindowHours == null || this.newWindowPct == null) {
      return;
    }
    this.windows.update((ws) => [
      ...ws,
      { hoursBefore: this.newWindowHours as number, penaltyPct: this.newWindowPct as number },
    ]);
    this.newWindowHours = null;
    this.newWindowPct = null;
    this.markDirty();
  }

  /** Removes a penalty window from the working set. */
  removeWindow(index: number): void {
    this.windows.update((ws) => ws.filter((_, i) => i !== index));
    this.markDirty();
  }

  /** Saves (upserts) the cancellation policy for the current scope. */
  save(): void {
    const ref = this.scopeRef.trim();
    if (!ref) {
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    const noShowFee =
      this.editNoShowFee != null
        ? { amount: this.editNoShowFee, currency: this.editNoShowCurrency }
        : null;
    this.cancellationService
      .put(ref, {
        type: this.editType,
        windows: this.windows(),
        refundable: this.editRefundable,
        costBearer: this.editCostBearer,
        merchantOfRecord: this.editMerchantOfRecord,
        noShowFee,
        waivedIfFlightCancelled: this.editWaivedIfFlightCancelled,
      })
      .subscribe({
        next: (view) => {
          this.saving.set(false);
          this.dirty = false;
          this.policy.set(view);
          this.seedForm(view);
          this.state.set('success');
        },
        error: (error: ApiError) => {
          this.saving.set(false);
          this.saveError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** PrimeNG Tag severity for the cancellation type. */
  typeSeverity(type: CancellationType): 'danger' | 'info' | 'secondary' {
    switch (type) {
      case 'ALL_SALES_FINAL':
        return 'danger';
      case 'CUSTOM':
        return 'info';
      default:
        return 'secondary';
    }
  }

  private seedForm(view: CancellationPolicyView): void {
    this.editType = view.type;
    this.editRefundable = view.refundable;
    this.editCostBearer = view.costBearer;
    this.editMerchantOfRecord = view.merchantOfRecord;
    this.editNoShowFee = view.noShowFee?.amount ?? null;
    this.editNoShowCurrency = view.noShowFee?.currency ?? 'BRL';
    this.editWaivedIfFlightCancelled = view.waivedIfFlightCancelled;
    this.windows.set([...view.windows]);
    this.dirty = false;
  }
}
