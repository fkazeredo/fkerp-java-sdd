import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { CadastroLabelPipe } from '../../core/cadastro/cadastro-label.pipe';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  FxPositionView,
  LiveExposureView,
  MarketRateResponse,
  PromoFxResultView,
} from './exchange.models';
import { ExchangeDeskService } from './exchange-desk.service';

/**
 * Operational FX desk screen (SPEC-0011, SPEC-0029 16b) — the companion to the pinned-rate screen
 * (SPEC-0003). It shows the book's live exposure (accrued subsidy + mark-to-market drift, with the
 * drift alert — BR6/BR9), records/reads the market rate and its history (manual contingency,
 * DL-0025), reads a booking's FX position with its subsidy × drift decomposition, and the promo-fx
 * period report (subsidy × drift × gap). Every data section uses {@link ScreenState}.
 */
@Component({
  selector: 'app-exchange-desk-page',
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
    CadastroLabelPipe,
  ],
  templateUrl: './exchange-desk-page.html',
})
export class ExchangeDeskPage implements OnInit, FormLeaveGuard {
  private readonly deskService = inject(ExchangeDeskService);

  readonly format = formatMoney;

  // Live exposure (loaded on init).
  readonly exposureState = signal<'loading' | 'success' | 'error'>('loading');
  readonly exposure = signal<LiveExposureView | null>(null);
  readonly exposureError = signal<string | null>(null);

  // Market rate: record form + history.
  ratePair = 'USD/BRL';
  newRate: number | null = null;
  readonly recording = signal(false);
  readonly recordError = signal<string | null>(null);
  readonly rateState = signal<'loading' | 'success' | 'error'>('loading');
  readonly rates = signal<MarketRateResponse[]>([]);
  readonly rateError = signal<string | null>(null);

  readonly rateListState = computed<ScreenStateKind>(() => {
    const s = this.rateState();
    if (s === 'success') {
      return this.rates().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Position lookup by booking.
  positionBookingId = '';
  readonly position = signal<FxPositionView | null>(null);
  readonly positionState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly positionError = signal<string | null>(null);

  // Promo-fx report.
  promoPeriod = '';
  readonly promo = signal<PromoFxResultView | null>(null);
  readonly promoState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly promoError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadExposure();
    this.loadRates();
  }

  /** Whether the record-rate form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.recording() && this.newRate != null;
  }

  /** Loads the book's live exposure. */
  loadExposure(): void {
    this.exposureState.set('loading');
    this.deskService.liveExposure().subscribe({
      next: (view) => {
        this.exposure.set(view);
        this.exposureState.set('success');
      },
      error: (error: ApiError) => {
        this.exposureError.set(error?.code ?? 'error.internal');
        this.exposureState.set('error');
      },
    });
  }

  /** Loads the market-rate history for the searched pair. */
  loadRates(): void {
    this.rateState.set('loading');
    this.deskService.marketRateHistory(this.ratePair).subscribe({
      next: (page) => {
        this.rates.set(page.content);
        this.rateState.set('success');
      },
      error: (error: ApiError) => {
        this.rateError.set(error?.code ?? 'error.internal');
        this.rateState.set('error');
      },
    });
  }

  /** Records a manual market-rate observation, then reloads the history. */
  recordRate(): void {
    if (this.newRate == null) {
      return;
    }
    this.recording.set(true);
    this.recordError.set(null);
    this.deskService.recordMarketRate({ currencyPair: this.ratePair, rate: this.newRate }).subscribe({
      next: () => {
        this.recording.set(false);
        this.newRate = null;
        this.loadRates();
      },
      error: (error: ApiError) => {
        this.recording.set(false);
        this.recordError.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** Looks up a booking's FX position. */
  lookupPosition(): void {
    const id = this.positionBookingId.trim();
    if (!id) {
      return;
    }
    this.positionState.set('loading');
    this.positionError.set(null);
    this.deskService.positionByBooking(id).subscribe({
      next: (view) => {
        this.position.set(view);
        this.positionState.set('success');
      },
      error: (error: ApiError) => {
        this.positionError.set(error?.code ?? 'error.internal');
        this.positionState.set('error');
      },
    });
  }

  /** Looks up the promo-fx report for a period. */
  lookupPromo(): void {
    const period = this.promoPeriod.trim();
    if (!period) {
      return;
    }
    this.promoState.set('loading');
    this.promoError.set(null);
    this.deskService.promoFx(period).subscribe({
      next: (view) => {
        this.promo.set(view);
        this.promoState.set('success');
      },
      error: (error: ApiError) => {
        this.promoError.set(error?.code ?? 'error.internal');
        this.promoState.set('error');
      },
    });
  }
}
