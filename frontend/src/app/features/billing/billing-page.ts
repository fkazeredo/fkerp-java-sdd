import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState } from '../../shared/screen-state/screen-state';
import { CommissionInvoiceView, InvoiceStatus } from './billing.models';
import { BillingService } from './billing.service';

/**
 * Billing screen (SPEC-0016, SPEC-0029 16a): looks up a commission invoice by id and shows its base
 * (the taxable commission — BR1, never the gross package), ISS, withholdings, status and number;
 * creates a draft, issues it (ISS/withholdings/number — requires ROLE_FINANCE) and cancels an issued
 * one. Each data section uses {@link ScreenState} for the loading/empty/error/permission states.
 */
@Component({
  selector: 'app-billing-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './billing-page.html',
})
export class BillingPage implements FormLeaveGuard {
  private readonly billingService = inject(BillingService);

  readonly format = formatMoney;

  /** Whether the draft form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.creating() && (!!this.draftCommissionEntryId || this.draftBase != null);
  }

  // Lookup state.
  invoiceId = '';
  readonly invoice = signal<CommissionInvoiceView | null>(null);
  readonly state = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly errorCode = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly busy = signal(false);

  // Draft form.
  draftCommissionEntryId = '';
  draftBase: number | null = null;
  draftCurrency = 'BRL';
  draftMunicipality = '';
  draftServiceCode = '';
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  // Cancel form.
  cancelReason = '';

  /** Looks up an invoice by id. */
  lookup(): void {
    const id = this.invoiceId.trim();
    if (!id) {
      return;
    }
    this.state.set('loading');
    this.errorCode.set(null);
    this.actionError.set(null);
    this.billingService.getById(id).subscribe({
      next: (invoice) => {
        this.invoice.set(invoice);
        this.state.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }

  /** Creates a draft invoice and shows it. */
  createDraft(): void {
    if (this.draftBase == null || !this.draftCommissionEntryId || !this.draftMunicipality) {
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    this.billingService
      .createDraft({
        commissionEntryId: this.draftCommissionEntryId,
        base: { amount: this.draftBase, currency: this.draftCurrency },
        municipality: this.draftMunicipality,
        serviceCode: this.draftServiceCode || null,
      })
      .subscribe({
        next: (invoice) => {
          this.creating.set(false);
          this.invoice.set(invoice);
          this.invoiceId = invoice.id;
          this.state.set('success');
        },
        error: (error: ApiError) => {
          this.creating.set(false);
          this.createError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Issues the current draft invoice (requires ROLE_FINANCE). */
  issue(): void {
    const current = this.invoice();
    if (!current) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.billingService.issue(current.id).subscribe({
      next: (invoice) => {
        this.busy.set(false);
        this.invoice.set(invoice);
      },
      error: (error: ApiError) => {
        this.busy.set(false);
        this.actionError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** Cancels the current issued invoice. */
  cancel(): void {
    const current = this.invoice();
    if (!current || !this.cancelReason.trim()) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.billingService.cancel(current.id, { reason: this.cancelReason.trim() }).subscribe({
      next: (invoice) => {
        this.busy.set(false);
        this.invoice.set(invoice);
        this.cancelReason = '';
      },
      error: (error: ApiError) => {
        this.busy.set(false);
        this.actionError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** PrimeNG Tag severity for an invoice status. */
  statusSeverity(status: InvoiceStatus): 'success' | 'danger' | 'secondary' {
    switch (status) {
      case 'EMITIDA':
        return 'success';
      case 'CANCELADA':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
