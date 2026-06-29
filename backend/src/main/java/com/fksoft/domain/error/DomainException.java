package com.fksoft.domain.error;

/**
 * Base type for all business (domain) exceptions (ADR 0011).
 *
 * <p>A domain exception carries <strong>only domain data</strong>: a stable {@code code} that is
 * also the i18n message key, plus optional message arguments. It deliberately carries <strong>no
 * transport concern</strong> (no {@code HttpStatus}, headers or response DTO). The presentation
 * layer ({@code com.fksoft.infra.web}) maps the exception type to an HTTP status via {@code
 * HttpErrorMapping} and resolves the message via the i18n {@code MessageSource}.
 *
 * <p>Extra domain data is exposed by implementing {@link ErrorDetails} (key/value pairs) or {@link
 * RateLimited} (a retry duration) — never by leaking transport classification into the domain.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;
  private final transient Object[] args;

  /**
   * @param code stable error code that is also the i18n message key (e.g. {@code
   *     account.document.duplicate}).
   * @param args optional message arguments resolved against the i18n message.
   */
  protected DomainException(String code, Object... args) {
    super(code);
    this.code = code;
    this.args = args == null ? new Object[0] : args.clone();
  }

  /** The stable error code, equal to the i18n message key. */
  public String code() {
    return code;
  }

  /** A defensive copy of the message arguments. */
  public Object[] args() {
    return args.clone();
  }
}
