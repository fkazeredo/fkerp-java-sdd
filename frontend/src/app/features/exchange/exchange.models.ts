import { Money } from '../../core/models/api.models';

/** Pinned sell rate returned by the backend (SPEC-0003). */
export interface PinnedSellRateResponse {
  id: string;
  currencyPair: string;
  rate: number;
  effectiveFrom: string;
  setBy: string;
  note: string | null;
}

/** Body for `POST /api/exchange/pinned-rates`. */
export interface PinRateRequest {
  currencyPair: string;
  rate: number;
  effectiveFrom?: string | null;
  note?: string | null;
}

/** Origin of a market-rate observation (SPEC-0011 BR1). */
export type MarketRateSource = 'FEED' | 'MANUAL';

/** Market-rate observation returned by the backend (SPEC-0011). */
export interface MarketRateResponse {
  id: string;
  currencyPair: string;
  rate: number;
  observedAt: string;
  source: MarketRateSource;
}

/** Body for `POST /api/exchange/market-rates` (manual contingency, DL-0025). */
export interface RecordMarketRateRequest {
  currencyPair: string;
  rate: number;
  observedAt?: string | null;
}

/** Status of an FX position (SPEC-0011). */
export type FxPositionStatus = 'OPEN' | 'CLOSED';

/** Read view of an FX position and its subsidy × drift decomposition (SPEC-0011). */
export interface FxPositionView {
  bookingId: string;
  foreignAmount: Money;
  pinnedRate: number;
  marketAtFreeze: number;
  subsidy: Money;
  markToMarketDrift: Money | null;
  settlementRate: number | null;
  realizedDrift: Money | null;
  totalGap: Money | null;
  status: FxPositionStatus;
  openedAt: string;
}

/** The book's live FX exposure (SPEC-0011 BR6; coverage fields from SPEC-0032/19h). */
export interface LiveExposureView {
  asOf: string;
  openPositions: number;
  accruedSubsidy: Money;
  markToMarketDrift: Money;
  totalExposure: Money;
  driftThreshold: Money;
  driftAlert: boolean;
  openForwards: number;
  unhedgedExposureBase: Money;
}

/** Lifecycle of an FX forward contract (SPEC-0032). */
export type ForwardStatus = 'OPEN' | 'SETTLED' | 'CANCELLED';

/** An FX forward contract — the treasury hedge instrument (SPEC-0032, 19h). */
export interface ForwardContractView {
  id: string;
  currency: string;
  notional: number;
  contractRate: number;
  tradeDate: string;
  maturityDate: string;
  counterparty: string;
  status: ForwardStatus;
  settledRate: number | null;
  settlementResultBrl: number | null;
  settledAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
}

/** Body for `POST /api/exchange/forwards`. */
export interface RegisterForwardRequest {
  currency: string;
  notional: number;
  contractRate: number;
  tradeDate: string;
  maturityDate: string;
  counterparty: string;
}

/** The FX promo result for a period (SPEC-0011, OVERVIEW 8.2-C). */
export interface PromoFxResultView {
  period: string;
  positions: number;
  subsidy: Money;
  drift: Money;
  totalGap: Money;
}
