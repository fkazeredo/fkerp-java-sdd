package com.fksoft.domain.platform;

import com.fksoft.domain.platform.internal.SystemAuditEntry;
import com.fksoft.domain.platform.internal.SystemAuditRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the consolidated system audit (SPEC-0023 BR4; DL-0077). It is the single
 * entry point any producer uses to record a security/integration/job fact, and the read side for
 * the {@code GET /audit} query. The audit is <strong>append-only</strong> (the entity has no
 * mutator) and carries <strong>metadata only</strong> — the {@code detail} JSON NEVER includes
 * secret material (BR1, security.md). The correlation id is taken from the MDC when present.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditService {

  private final SystemAuditRepository auditRepository;
  private final Clock clock;

  /**
   * Records a system-audit fact (BR4). The detail MUST be metadata only — callers never pass secret
   * material (BR1).
   *
   * @param type the audit type
   * @param actor who (or {@code null} for system); callers mask personal data
   * @param detailJson the metadata JSON (no secrets)
   * @return the persisted entry view
   */
  @Transactional
  public SystemAuditView record(AuditType type, String actor, String detailJson) {
    Instant now = clock.instant();
    SystemAuditEntry entry =
        SystemAuditEntry.record(type, actor, detailJson, MDC.get("correlationId"), now);
    auditRepository.save(entry);
    return entry.toView();
  }

  /**
   * The filtered audit trail (SPEC-0023 — {@code GET /audit}), newest first, paginated.
   *
   * @param actor optional actor filter
   * @param type optional type filter
   * @param from optional lower time bound (inclusive)
   * @param to optional upper time bound (inclusive)
   * @param pageable the page request
   * @return the page of audit views
   */
  @Transactional(readOnly = true)
  public Page<SystemAuditView> search(
      String actor, AuditType type, Instant from, Instant to, Pageable pageable) {
    Specification<SystemAuditEntry> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (actor != null) {
            predicates.add(cb.equal(root.get("actor"), actor));
          }
          if (type != null) {
            predicates.add(cb.equal(root.get("type"), type));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Pageable sorted =
        pageable.getSort().isSorted()
            ? pageable
            : org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
    return auditRepository.findAll(spec, sorted).map(SystemAuditEntry::toView);
  }
}
