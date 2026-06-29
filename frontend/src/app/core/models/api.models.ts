/** Money value mirroring the backend `{amount, currency}` (amount serialized as a JSON number). */
export interface Money {
  amount: number;
  currency: string;
}

/** Pagination envelope mirroring the backend `PageResponse<T>` (ADR 0012). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Formats a money value as `CUR 0.00`, or an em dash when absent. */
export function formatMoney(money: Money | null | undefined): string {
  if (!money) {
    return '—';
  }
  return `${money.currency} ${Number(money.amount).toFixed(2)}`;
}
