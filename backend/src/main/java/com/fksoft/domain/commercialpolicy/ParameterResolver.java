package com.fksoft.domain.commercialpolicy;

import com.fksoft.domain.ModuleInternal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pure precedence engine (SPEC-0014 BR2/BR3, DL-0037): given the candidate rules for a key, the
 * resolution date and the query scope, it picks the active, highest-precedence rule whose scope
 * matches. Kept side-effect-free and stateless so the ordering invariant is unit-testable in
 * isolation (no DB), exactly like the other pure domain rules in the project.
 *
 * <p>Total order (winner = minimum): layer rank ASC ({@code DIRECTIVE}=0 … {@code
 * SYSTEM_DEFAULT}=4) → scope specificity DESC (more specific wins within a layer) → {@code
 * validFrom} DESC → {@code createdAt} DESC → {@code id} ASC. The {@code id} tie-break makes the
 * result deterministic — two rules never share an id.
 */
@ModuleInternal
public final class ParameterResolver {

  private static final Comparator<ParameterRule> PRECEDENCE =
      Comparator.comparingInt((ParameterRule r) -> r.layer().rank())
          .thenComparing(Comparator.comparingInt(ParameterRule::specificity).reversed())
          .thenComparing(Comparator.comparing(ParameterRule::validFrom).reversed())
          .thenComparing(Comparator.comparing(ParameterRule::createdAt).reversed())
          .thenComparing(ParameterRule::id);

  private ParameterResolver() {}

  /**
   * The winning rule among {@code candidates} for the query, or empty when none is active and
   * matching.
   *
   * @param candidates all rules for the key (any layer/scope)
   * @param on the resolution date
   * @param query the query scope
   * @return the highest-precedence active matching rule
   */
  public static Optional<ParameterRule> resolve(
      List<ParameterRule> candidates, LocalDate on, ParameterScope query) {
    return candidates.stream()
        .filter(rule -> rule.isActiveOn(on))
        .filter(rule -> rule.matches(query))
        .min(PRECEDENCE);
  }
}
