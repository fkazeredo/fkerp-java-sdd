package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal repository for the custodied certificate aggregate (SPEC-0023). Only the {@code
 * platform} domain reaches it (Spring Modulith). Reads return the most recent certificate as the
 * active one (the custody holds the current e-CNPJ).
 */
@ModuleInternal
public interface PlatformCertificateRepository extends JpaRepository<PlatformCertificate, UUID> {

  /** The currently active certificate (most recently custodied), if any. */
  Optional<PlatformCertificate> findFirstByOrderByCreatedAtDesc();

  /** All certificates ordered newest first (for the expiry sweep). */
  List<PlatformCertificate> findAllByOrderByCreatedAtDesc();
}
