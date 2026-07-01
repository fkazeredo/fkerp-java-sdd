package com.fksoft.domain.cadastro;

/**
 * Command to create a new cadastro item (SPEC-0031 BR1). The {@code code} is immutable once
 * created; {@code label} and {@code sortOrder} are the human/ordering data.
 *
 * @param type the cadastro type (required)
 * @param code the immutable code (required)
 * @param label the human label in pt-BR (required)
 * @param sortOrder the display order (lower first)
 */
public record CreateCadastroItemCommand(
    CadastroType type, String code, String label, int sortOrder) {}
