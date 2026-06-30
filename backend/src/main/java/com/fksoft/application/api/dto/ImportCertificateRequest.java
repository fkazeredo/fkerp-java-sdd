package com.fksoft.application.api.dto;

import com.fksoft.domain.platform.ImportCertificateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Request to custody an e-CNPJ certificate (SPEC-0023 BR1). The secret material is passed as
 * base64; it is encrypted-at-rest immediately and NEVER returned, logged or echoed (BR1). The
 * response exposes only metadata.
 *
 * @param subject the certificate subject DN
 * @param holderDocument the holder CNPJ
 * @param validFrom validity start (ISO date)
 * @param validUntil validity end (ISO date)
 * @param materialBase64 the secret certificate bytes, base64-encoded (write-only)
 */
public record ImportCertificateRequest(
    @NotBlank String subject,
    @NotBlank String holderDocument,
    @NotNull LocalDate validFrom,
    @NotNull LocalDate validUntil,
    @NotBlank String materialBase64) {

  /** Translates to the domain command, decoding the secret material. */
  public ImportCertificateCommand toCommand() {
    return new ImportCertificateCommand(
        subject, holderDocument, validFrom, validUntil, Base64.getDecoder().decode(materialBase64));
  }
}
