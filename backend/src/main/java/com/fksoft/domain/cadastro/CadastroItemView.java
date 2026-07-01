package com.fksoft.domain.cadastro;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a cadastro item (SPEC-0031). The delivery layer returns this record, never
 * the {@code CadastroItem} entity (the model stays inside the module).
 *
 * @param id the item id
 * @param type the cadastro type
 * @param code the immutable code (= old enum constant name)
 * @param label the human label (pt-BR)
 * @param active whether the item is active
 * @param sortOrder the display order (lower first)
 * @param createdAt when it was created
 */
public record CadastroItemView(
    UUID id,
    CadastroType type,
    String code,
    String label,
    boolean active,
    int sortOrder,
    Instant createdAt) {}
