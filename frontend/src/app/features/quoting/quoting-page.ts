import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiError } from '../../core/http/api-error';
import { formatMoney } from '../../core/models/api.models';
import { QuoteView } from './quoting.models';
import { QuotingService } from './quoting.service';

/**
 * Quoting screen (SPEC-0005, keystone): composes a MANUAL quote and shows the suggested vs applied
 * amount with the full commission decomposition and provenance, then lets a human apply an override
 * with a mandatory reason — the divergence recorded against the suggestion.
 */
@Component({
  selector: 'app-quoting-page',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './quoting-page.html',
})
export class QuotingPage {
  private readonly quotingService = inject(QuotingService);

  readonly quote = signal<QuoteView | null>(null);
  readonly composeError = signal<string | null>(null);
  readonly overrideError = signal<string | null>(null);
  readonly busy = signal(false);

  readonly format = formatMoney;

  accountId = '';
  baseAmount: number | null = 500;
  baseCurrency = 'USD';
  currencyPair = 'USD/BRL';
  supplierPct: number | null = 0.15;
  agentPct: number | null = 0.1;

  appliedAmount: number | null = null;
  reason = '';

  /** Composes the quote from the form and shows the result. */
  compose(): void {
    if (this.baseAmount == null || this.supplierPct == null || this.agentPct == null) {
      return;
    }
    this.busy.set(true);
    this.composeError.set(null);
    this.quotingService
      .compose({
        accountId: this.accountId,
        basePrice: { amount: this.baseAmount, currency: this.baseCurrency },
        currencyPair: this.currencyPair,
        supplierCommissionPct: this.supplierPct,
        agentCommissionPct: this.agentPct,
      })
      .subscribe({
        next: (quote) => {
          this.busy.set(false);
          this.quote.set(quote);
          this.appliedAmount = quote.appliedAmount.amount;
        },
        error: (error: ApiError) => {
          this.busy.set(false);
          this.composeError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Applies a price override against the current quote. */
  applyOverride(): void {
    const current = this.quote();
    if (!current || this.appliedAmount == null) {
      return;
    }
    this.busy.set(true);
    this.overrideError.set(null);
    this.quotingService
      .override(current.id, {
        appliedAmount: { amount: this.appliedAmount, currency: current.suggestedAmount.currency },
        reason: this.reason,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.quote.set(updated);
          this.reason = '';
        },
        error: (error: ApiError) => {
          this.busy.set(false);
          this.overrideError.set(error?.code ?? 'error.internal');
        },
      });
  }
}
