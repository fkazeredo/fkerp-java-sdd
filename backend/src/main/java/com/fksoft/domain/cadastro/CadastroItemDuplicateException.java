package com.fksoft.domain.cadastro;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when creating a cadastro item whose {@code (type, code)} already exists (SPEC-0031 BR1 —
 * unique). Mapped to {@code 409 Conflict}.
 */
public class CadastroItemDuplicateException extends DomainException {

  public CadastroItemDuplicateException() {
    super("cadastro.item.duplicate");
  }
}
