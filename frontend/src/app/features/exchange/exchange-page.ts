import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { PinnedSellRateResponse } from './exchange.models';
import { ExchangeService } from './exchange.service';

/**
 * Exchange screen (SPEC-0003, repaginated SPEC-0026): a "pin rate" form plus the append-only history
 * for a currency pair, with the loading/empty/error/permission states (BR8) via {@link ScreenState}.
 * The prevailing rate is simply the newest history row. Implements {@link FormLeaveGuard} (BR9).
 */
@Component({
  selector: 'app-exchange-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    TableModule,
    ScreenState,
  ],
  templateUrl: './exchange-page.html',
})
export class ExchangePage implements OnInit, FormLeaveGuard {
  private readonly exchangeService = inject(ExchangeService);

  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly rates = signal<PinnedSellRateResponse[]>([]);
  readonly errorCode = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.rates().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  pair = 'USD/BRL';
  currencyPair = 'USD/BRL';
  rate: number | null = null;
  note = '';

  ngOnInit(): void {
    this.load();
  }

  /** Whether a rate value has been entered but not yet pinned (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.submitting() && this.rate != null;
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
          this.rate = null;
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
