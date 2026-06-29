package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/compliance/documents/{id}/attach} (SPEC-0008): links a stored
 * document to a financial entry by value.
 *
 * @param entryId the financial entry id (required)
 * @param entryType the entry's business type, as a value (required)
 */
public record AttachDocumentRequest(@NotNull UUID entryId, @NotBlank String entryType) {}
