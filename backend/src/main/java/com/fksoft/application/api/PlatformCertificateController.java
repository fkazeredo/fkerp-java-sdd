package com.fksoft.application.api;

import com.fksoft.application.api.dto.ImportCertificateRequest;
import com.fksoft.domain.platform.CertificateCustodyService;
import com.fksoft.domain.platform.CertificateView;
import com.fksoft.infra.security.UserContextProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the e-CNPJ certificate custody (SPEC-0023 BR1) — slice 8j-1. The status
 * returns <strong>only metadata</strong> (subject, validity, days-to-expiry, status); the
 * certificate material is NEVER returned by any endpoint (BR1, security.md). Importing accepts the
 * secret material (encrypted at rest immediately) and responds with the same secret-free view.
 */
@Tag(name = "Platform Certificate", description = "Custódia do e-CNPJ (só metadados)")
@RestController
@RequestMapping("/api/platform/certificate")
@RequiredArgsConstructor
public class PlatformCertificateController {

  private final CertificateCustodyService custodyService;
  private final UserContextProvider userContextProvider;

  /** The current certificate status — metadata only (BR1). 404 when none is custodied. */
  @GetMapping("/status")
  public CertificateView status() {
    return custodyService.status();
  }

  /**
   * Custodies a certificate (TI/admin). The material is encrypted at rest; only metadata returns.
   */
  @PostMapping
  public ResponseEntity<CertificateView> importCertificate(
      @Valid @RequestBody ImportCertificateRequest request) {
    CertificateView view = custodyService.importCertificate(request.toCommand(), currentActor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  private String currentActor() {
    return userContextProvider.currentUser().username();
  }
}
