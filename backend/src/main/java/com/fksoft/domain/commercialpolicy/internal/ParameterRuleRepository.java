package com.fksoft.domain.commercialpolicy.internal;

import com.fksoft.domain.commercialpolicy.ParameterLayer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal repository for {@link ParameterRule}. Resolution loads the candidate rules for a
 * key (a small set per key) and applies the precedence/specificity/tie-break in memory (DL-0037),
 * where the deterministic ordering is unit-tested; the listing query supports audit/curation.
 */
public interface ParameterRuleRepository extends JpaRepository<ParameterRule, UUID> {

  /** All rules for a key (across layers/scopes) — the candidate set for resolution. */
  List<ParameterRule> findByParameterKey(String parameterKey);

  /** Whether any rule exists for a key (used to detect a key with no SYSTEM_DEFAULT, BR4). */
  boolean existsByParameterKey(String parameterKey);

  /** Listing for audit/curation, optionally filtered by key and/or layer (nulls = no filter). */
  @org.springframework.data.jpa.repository.Query(
      """
      select r from ParameterRule r
      where (:parameterKey is null or r.parameterKey = :parameterKey)
        and (:layer is null or r.layer = :layer)
      order by r.parameterKey asc, r.layer asc, r.createdAt desc
      """)
  List<ParameterRule> list(String parameterKey, ParameterLayer layer);
}
