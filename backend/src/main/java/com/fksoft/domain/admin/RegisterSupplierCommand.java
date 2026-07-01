package com.fksoft.domain.admin;

/**
 * Command to register an administrative supplier (SPEC-0025 BR1).
 *
 * @param type the supplier-type cadastro code (required)
 * @param identifier the legal identifier (CNPJ/CPF when applicable), or {@code null}
 * @param displayName the display name (required)
 */
public record RegisterSupplierCommand(String type, String identifier, String displayName) {}
