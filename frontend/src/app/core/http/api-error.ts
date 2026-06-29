/** A single field-level violation, mirroring the backend ApiErrorResponse.FieldViolation. */
export interface ApiFieldViolation {
  field: string;
  message: string;
}

/**
 * Normalized API error, mirroring the backend's stable error contract `{code, message, fields}`
 * (ADR 0011). The HTTP error interceptor maps any failed response into this shape so features can
 * present errors by their stable `code`.
 */
export interface ApiError {
  code: string;
  message: string;
  fields: ApiFieldViolation[];
}
