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
