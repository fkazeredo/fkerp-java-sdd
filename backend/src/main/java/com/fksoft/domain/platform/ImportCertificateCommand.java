package com.fksoft.domain.platform;

import java.time.LocalDate;

/**
 * Command to custody a new e-CNPJ certificate (SPEC-0023 BR1). The {@code material} is the secret
 * bytes (PFX/PEM) and is encrypted-at-rest immediately (DL-0074); it is NEVER persisted in clear,
 * logged or echoed back. Only the metadata is exposed afterwards.
 *
 * @param subject the certificate subject DN (metadata)
 * @param holderDocument the holder CNPJ (metadata)
 * @param validFrom validity start
 * @param validUntil validity end
 * @param material the secret certificate bytes — encrypted before storage, never logged
 */
public record ImportCertificateCommand(
    String subject,
    String holderDocument,
    LocalDate validFrom,
    LocalDate validUntil,
    byte[] material) {}
