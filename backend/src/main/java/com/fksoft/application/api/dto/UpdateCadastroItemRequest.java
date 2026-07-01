package com.fksoft.application.api.dto;

import com.fksoft.domain.cadastro.UpdateCadastroItemCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/cadastro/items/{id}} (SPEC-0031 BR2). Updates the editable
 * fields of an item; the {@code code} and {@code type} never change.
 *
 * @param label the new label in pt-BR (required)
 * @param active the new active flag
 * @param sortOrder the new display order
 */
public record UpdateCadastroItemRequest(@NotBlank String label, boolean active, int sortOrder) {

  /** Translates this request to the domain command. */
  public UpdateCadastroItemCommand toCommand() {
    return new UpdateCadastroItemCommand(label, active, sortOrder);
  }
}
