import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  ForwardContractView,
  FxPositionView,
  LiveExposureView,
  MarketRateResponse,
  PromoFxResultView,
  RecordMarketRateRequest,
  RegisterForwardRequest,
} from './exchange.models';

/**
 * Feature API service for the operational FX desk (SPEC-0011): the market rate (record/current/
 * history), a booking's FX position, the book's live exposure and the promo-fx period report. The
 * pinned sell rate stays in {@link ExchangeService} (SPEC-0003).
 */
@Injectable({ providedIn: 'root' })
export class ExchangeDeskService {
  private readonly http = inject(HttpClient);

  /** Records a manual market-rate observation (contingency path, DL-0025). */
  recordMarketRate(request: RecordMarketRateRequest): Observable<MarketRateResponse> {
    return this.http.post<MarketRateResponse>(`${API_BASE_URL}/exchange/market-rates`, request);
  }

  /** The prevailing market rate for a pair (e.g. `USD/BRL`). */
  currentMarketRate(pair: string): Observable<MarketRateResponse> {
    const params = new HttpParams().set('pair', pair);
    return this.http.get<MarketRateResponse>(`${API_BASE_URL}/exchange/market-rates/current`, {
      params,
    });
  }

  /** The market-rate observation history for a pair, newest first. */
  marketRateHistory(pair: string): Observable<PageResponse<MarketRateResponse>> {
    const params = new HttpParams().set('pair', pair);
    return this.http.get<PageResponse<MarketRateResponse>>(
      `${API_BASE_URL}/exchange/market-rates`,
      { params },
    );
  }

  /** The FX position born from a booking, with its subsidy × drift decomposition. */
  positionByBooking(bookingId: string): Observable<FxPositionView> {
    return this.http.get<FxPositionView>(`${API_BASE_URL}/exchange/positions/${bookingId}`);
  }

  /** The book's live FX exposure (aggregate subsidy + drift + drift alert). */
  liveExposure(): Observable<LiveExposureView> {
    return this.http.get<LiveExposureView>(`${API_BASE_URL}/exchange/exposure`);
  }

  /** The promo-fx result for a period (`YYYY-MM`): subsidy × drift × gap. */
  promoFx(period: string): Observable<PromoFxResultView> {
    const params = new HttpParams().set('period', period);
    return this.http.get<PromoFxResultView>(`${API_BASE_URL}/exchange/reports/promo-fx`, {
      params,
    });
  }

  /** Lists FX forward contracts (SPEC-0032), newest first. */
  listForwards(): Observable<ForwardContractView[]> {
    return this.http.get<ForwardContractView[]>(`${API_BASE_URL}/exchange/forwards`);
  }

  /** Registers a forward (treasury hedge — Director/Finance). */
  registerForward(request: RegisterForwardRequest): Observable<ForwardContractView> {
    return this.http.post<ForwardContractView>(`${API_BASE_URL}/exchange/forwards`, request);
  }

  /** Settles a forward at the effective market rate. */
  settleForward(id: string, effectiveRate: number): Observable<ForwardContractView> {
    return this.http.post<ForwardContractView>(`${API_BASE_URL}/exchange/forwards/${id}/settle`, {
      effectiveRate,
    });
  }

  /** Cancels an OPEN forward (stops counting as coverage). */
  cancelForward(id: string): Observable<ForwardContractView> {
    return this.http.post<ForwardContractView>(
      `${API_BASE_URL}/exchange/forwards/${id}/cancel`,
      null,
    );
  }
}
