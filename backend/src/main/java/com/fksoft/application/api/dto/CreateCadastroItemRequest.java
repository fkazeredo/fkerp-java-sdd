package com.fksoft.application.api.dto;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CreateCadastroItemCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/cadastro/items} (SPEC-0031 BR1). Creates a new reference-data
 * item. The {@code code} is immutable once created.
 *
 * @param type the cadastro type (required)
 * @param code the immutable code (required)
 * @param label the human label in pt-BR (required)
 * @param sortOrder the display order (lower first)
 */
public record CreateCadastroItemRequest(
    @NotNull CadastroType type,
    @NotBlank String code,
    @NotBlank String label,
    int sortOrder) {

  /** Translates this request to the domain command. */
  public CreateCadastroItemCommand toCommand() {
    return new CreateCadastroItemCommand(type, code, label, sortOrder);
  }
}
