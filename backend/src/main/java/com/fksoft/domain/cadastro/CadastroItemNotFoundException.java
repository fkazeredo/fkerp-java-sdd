package com.fksoft.domain.cadastro;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a cadastro item is looked up by an id that does not exist (SPEC-0031). Mapped to {@code
 * 404 Not Found}.
 */
public class CadastroItemNotFoundException extends DomainException {

  public CadastroItemNotFoundException() {
    super("cadastro.item.not-found");
  }
}
