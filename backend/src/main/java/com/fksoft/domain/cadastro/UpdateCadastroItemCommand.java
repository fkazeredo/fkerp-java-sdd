package com.fksoft.domain.cadastro;

/**
 * Command to update the editable fields of a cadastro item (SPEC-0031 BR2): {@code label}, {@code
 * active} and {@code sortOrder}. The {@code code} and {@code type} never change.
 *
 * @param label the new label in pt-BR (required)
 * @param active the new active flag
 * @param sortOrder the new display order
 */
public record UpdateCadastroItemCommand(String label, boolean active, int sortOrder) {}
