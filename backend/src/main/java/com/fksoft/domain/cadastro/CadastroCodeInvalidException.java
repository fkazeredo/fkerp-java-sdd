package com.fksoft.domain.cadastro;

/**
 * Raised when another module writes a {@code code} that does not exist or is not active for a {@link
 * CadastroType} (SPEC-0031 BR3; ADR-0019/DL-0115). This is the runtime guard that replaces the
 * compile-time enum check: a converted field only accepts a validated, active reference code. Mapped
 * to {@code 422 Unprocessable Content} (the default for an unmapped domain exception) — the request
 * is well-formed but the code is not an acceptable reference value.
 *
 * <p>The offending {@code type}/{@code code} are carried as message arguments for the i18n message.
 */
public class CadastroCodeInvalidException extends com.fksoft.domain.error.DomainException {

  public CadastroCodeInvalidException(CadastroType type, String code) {
    super("cadastro.code.invalid", type == null ? "" : type.name(), code == null ? "" : code);
  }
}
