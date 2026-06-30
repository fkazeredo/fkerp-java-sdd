package com.fksoft.application.api.dto;

import com.fksoft.domain.admin.AdminSupplierType;
import com.fksoft.domain.admin.RegisterSupplierCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/admin/suppliers} (SPEC-0025 BR1).
 *
 * @param type the supplier type (required)
 * @param identifier the legal identifier (CNPJ/CPF), or {@code null}
 * @param displayName the display name (required)
 */
public record RegisterAdminSupplierRequest(
    @NotNull AdminSupplierType type, String identifier, @NotBlank String displayName) {

  /** Translates this request to the domain command. */
  public RegisterSupplierCommand toCommand() {
    return new RegisterSupplierCommand(type, identifier, displayName);
  }
}
