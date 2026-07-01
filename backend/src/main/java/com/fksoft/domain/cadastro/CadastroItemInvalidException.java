package com.fksoft.domain.cadastro;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when creating/updating a cadastro item with missing or invalid mandatory data — type, code
 * or label (SPEC-0031 BR1/BR2). Mapped to {@code 400 Bad Request}.
 */
public class CadastroItemInvalidException extends DomainException {

  public CadastroItemInvalidException() {
    super("cadastro.item.invalid");
  }
}
