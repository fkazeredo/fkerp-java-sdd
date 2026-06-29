import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiError } from '../../core/http/api-error';
import { PinnedSellRateResponse } from './exchange.models';
import { ExchangeService } from './exchange.service';

type ViewState = 'loading' | 'success' | 'error';

/**
 * Exchange screen (SPEC-0003): a "pin rate" form plus the append-only history for a currency pair,
 * with loading/empty/error states. The prevailing rate is simply the newest history row.
 */
@Component({
  selector: 'app-exchange-page',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './exchange-page.html',
})
export class ExchangePage implements OnInit {
  private readonly exchangeService = inject(ExchangeService);

  readonly state = signal<ViewState>('loading');
  readonly rates = signal<PinnedSellRateResponse[]>([]);
  readonly errorCode = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  pair = 'USD/BRL';
  currencyPair = 'USD/BRL';
  rate: number | null = null;
  note = '';

  ngOnInit(): void {
    this.load();
  }

  /** Loads the history for the searched pair. */
  load(): void {
    this.state.set('loading');
    this.exchangeService.history(this.pair).subscribe({
      next: (page) => {
        this.rates.set(page.content);
        this.state.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }

  /** Pins a new rate, then shows that pair's history. */
  submit(): void {
    if (this.rate == null) {
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);
    this.exchangeService
      .pin({ currencyPair: this.currencyPair, rate: this.rate, note: this.note || null })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.note = '';
          this.pair = this.currencyPair;
          this.load();
        },
        error: (error: ApiError) => {
          this.submitting.set(false);
          this.submitError.set(error?.code ?? 'error.internal');
        },
      });
  }
}
