package com.fksoft.infra.web;

import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.error.ErrorDetails;
import com.fksoft.domain.error.RateLimited;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single global error handler (ADR 0011): translates exceptions into the stable {@link
 * ApiErrorResponse} contract, resolving messages through the i18n {@link MessageSource} and the
 * HTTP status through {@link HttpErrorMapping}. No transport concern lives in the domain; it is all
 * resolved here.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageSource messageSource;
  private final HttpErrorMapping errorMapping;

  /** Maps a business exception to its configured status and the i18n message for its code. */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex) {
    HttpStatus status = errorMapping.statusFor(ex.getClass());
    String message = resolve(ex.code(), ex.args());

    List<ApiErrorResponse.FieldViolation> fields = List.of();
    if (ex instanceof ErrorDetails details) {
      fields =
          details.details().entrySet().stream()
              .map(
                  e ->
                      new ApiErrorResponse.FieldViolation(e.getKey(), String.valueOf(e.getValue())))
              .toList();
    }

    HttpHeaders headers = new HttpHeaders();
    if (ex instanceof RateLimited rateLimited) {
      headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimited.retryAfter().toSeconds()));
    }

    log.info("Domain error: code={} status={}", ex.code(), status.value());
    return ResponseEntity.status(status)
        .headers(headers)
        .body(new ApiErrorResponse(ex.code(), message, fields));
  }

  /** Maps bean-validation failures to {@code 400} with one field violation per rejected field. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiErrorResponse.FieldViolation> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiErrorResponse.FieldViolation(fe.getField(), fe.getDefaultMessage()))
            .toList();
    String message = resolve("validation.failed", new Object[0]);
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("validation.failed", message, fields));
  }

  /** Last-resort handler: never leaks internal details; logs the cause for diagnosis. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    String message = resolve("error.internal", new Object[0]);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of("error.internal", message));
  }

  private String resolve(String code, Object[] args) {
    return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
  }
}
