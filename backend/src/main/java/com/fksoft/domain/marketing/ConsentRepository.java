package com.fksoft.domain.marketing;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Command/projection repository for the {@link Consent} append-only log (SPEC-0019; DL-0056).
 * Module-internal: other modules never touch it (Spring Modulith). The current state for a
 * subject+purpose is the <strong>most recent row</strong> (by {@code createdAt}, tie-broken by
 * {@code id}); the send filter (BR2) reads it row-by-row through {@link #findLatest}.
 */
@ModuleInternal
public interface ConsentRepository extends JpaRepository<Consent, UUID> {

  /**
   * The latest consent row for a subject+purpose — the current state (DL-0056). Ordered by {@code
   * createdAt} then {@code id} descending so re-consent after revoke resolves to GRANTED.
   */
  @Query(
      "select c from Consent c where c.subjectType = :type and c.subjectId = :subjectId "
          + "and c.purpose = :purpose order by c.createdAt desc, c.id desc")
  List<Consent> findLatestRows(
      @Param("type") String type,
      @Param("subjectId") String subjectId,
      @Param("purpose") String purpose,
      Pageable pageable);

  /** Convenience: the single latest row for a subject+purpose, if any. */
  default Optional<Consent> findLatest(String type, String subjectId, String purpose) {
    List<Consent> rows = findLatestRows(type, subjectId, purpose, Pageable.ofSize(1));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** The full history for a subject+purpose, newest first (for the GET state+history view). */
  @Query(
      "select c from Consent c where c.subjectType = :type and c.subjectId = :subjectId "
          + "and c.purpose = :purpose order by c.createdAt desc, c.id desc")
  List<Consent> findHistory(
      @Param("type") String type,
      @Param("subjectId") String subjectId,
      @Param("purpose") String purpose);

  /** All rows for a subject across purposes (for the LGPD erasure, DL-0058). */
  @Query("select c from Consent c where c.subjectType = :type and c.subjectId = :subjectId")
  List<Consent> findAllForSubject(
      @Param("type") String type, @Param("subjectId") String subjectId);

  /**
   * The distinct subjects (type + id) that have <strong>any</strong> consent row for a purpose —
   * the candidate base a campaign for that purpose draws from in v1 (DL-0059: the send starts from
   * the consent base the module owns). The current GRANTED/REVOKED status of each is then resolved
   * by {@link #findLatest} so the filter (BR2) suppresses the revoked ones.
   */
  @Query(
      "select distinct c.subjectType, c.subjectId from Consent c where c.purpose = :purpose "
          + "order by c.subjectType, c.subjectId")
  List<Object[]> findDistinctSubjectsForPurpose(@Param("purpose") String purpose);
}
