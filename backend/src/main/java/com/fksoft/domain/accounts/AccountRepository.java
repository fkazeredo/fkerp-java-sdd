package com.fksoft.domain.accounts;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate repository for {@link Account}. Module-internal: only the accounts module uses it. The
 * unique index on {@code (legal_type, document_number)} is the authoritative duplicate guard (BR3);
 * {@link #existsByLegalTypeAndDocumentNumber} is the friendly pre-check on top of it.
 */
@ModuleInternal
public interface AccountRepository extends JpaRepository<Account, UUID> {

  /** Whether an account already exists for the given legal type and normalized document digits. */
  boolean existsByLegalTypeAndDocumentNumber(LegalType legalType, String documentNumber);

  /**
   * Resolves an account by its normalized document digits, irrespective of legal type (SPEC-0009,
   * DL-0017). In the v1 cadastre there is no collision between legal types for the same number.
   */
  Optional<Account> findFirstByDocumentNumber(String documentNumber);

  /**
   * Paginated search with optional filters: both {@code status} and {@code document} (normalized
   * digits) are applied only when non-null. An empty result yields an empty page (never 404).
   */
  @Query(
      "select a from Account a where (:status is null or a.status = :status) "
          + "and (:document is null or a.documentNumber = :document)")
  Page<Account> search(
      @Param("status") AccountStatus status, @Param("document") String document, Pageable pageable);
}
