package com.fksoft.infra.web;

import java.util.List;

/**
 * Stable JSON error contract returned by every endpoint (ADR 0011): {@code {code, message,
 * fields}}. {@code code} is the stable domain error code (== i18n key), {@code message} is the
 * resolved, internationalized message, and {@code fields} carries optional per-field violations.
 */
public record ApiErrorResponse(String code, String message, List<FieldViolation> fields) {

  /** A single field-level violation (field name + resolved message). */
  public record FieldViolation(String field, String message) {}

  /** Builds a response with no field violations. */
  public static ApiErrorResponse of(String code, String message) {
    return new ApiErrorResponse(code, message, List.of());
  }
}
