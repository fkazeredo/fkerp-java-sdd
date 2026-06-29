package com.fksoft.domain.commercialpolicy;

import com.fksoft.domain.commercialpolicy.internal.ParameterResolver;
import com.fksoft.domain.commercialpolicy.internal.ParameterRule;
import com.fksoft.domain.commercialpolicy.internal.ParameterRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the CommercialPolicy module (SPEC-0014). Owns the governed-parameter
 * <strong>precedence engine</strong> ({@link #resolve}) and the auditable definition of rules and
 * directives ({@link #defineRule}). It also <strong>implements the {@link MarkupProvider}
 * port</strong> consumed by Quoting — graduating the SPEC-0005 stub: the markup now flows from the
 * engine and carries the winning layer as its source (DL-0040).
 *
 * <p>Resolution is pure/Open-Host (BR6): it never mutates state nor calls another module. Only
 * {@link #defineRule} mutates, and it is audited; a {@code DIRECTIVE} carries reinforced audit
 * (BR5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialPolicyService implements MarkupProvider {

  private final ParameterRuleRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Resolves a parameter for a scope (SPEC-0014 BR2/BR3): the active, highest-precedence rule whose
   * scope matches, with its provenance. Precedence: layer rank first ({@code DIRECTIVE} … {@code
   * SYSTEM_DEFAULT}), then scope specificity (more specific wins within a layer), then a
   * deterministic tie-break (newest effectivity, newest creation, id) — DL-0037.
   *
   * @param key the parameter key
   * @param scope the resolution scope (use {@link ParameterScope#global()} for global)
   * @return the winning value + provenance
   * @throws PolicyParameterUnknownException when the key has no rule at all / no SYSTEM_DEFAULT
   *     (BR4)
   */
  @Transactional(readOnly = true)
  public ResolvedParameter resolve(ParameterKey key, ParameterScope scope) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    ParameterRule winner =
        ParameterResolver.resolve(repository.findByParameterKey(key.value()), today, scope)
            .orElseThrow(PolicyParameterUnknownException::new);
    log.info(
        "policy.resolved key={} layer={} ruleId={} scopeAccount={}",
        key.value(),
        winner.layer(),
        winner.id(),
        scope.accountId());
    return winner.toResolved();
  }

  /**
   * Defines a governed rule (SPEC-0014 {@code POST /rules} and {@code /directives}). Validates
   * value/ type coherence and effectivity (Validation Rules), enforces the authorization for the
   * layer (BR5/BR7, DL-0038), persists with audit, and publishes the business event(s). The new
   * rule is reflected immediately by the next {@link #resolve} (no cache).
   *
   * @param command the rule definition
   * @param roles the acting user's roles (authorization, DL-0038)
   * @param actor who defines it (audit)
   * @return the persisted rule view
   * @throws PolicyDirectiveForbiddenException when the role is insufficient for the layer (403)
   * @throws PolicyRuleInvalidException when value/type or effectivity is malformed (400)
   */
  @Transactional
  public ParameterRuleView defineRule(DefineRuleCommand command, Set<String> roles, String actor) {
    authorize(command.layer(), roles);
    String value = validateValue(command.value(), command.type());
    LocalDate validFrom =
        command.validFrom() != null
            ? command.validFrom()
            : LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    validateEffectivity(validFrom, command.validUntil());
    if (command.layer().isDirective() && isBlank(command.justification())) {
      throw new PolicyRuleInvalidException();
    }

    Instant now = clock.instant();
    ParameterRule rule =
        repository.save(
            ParameterRule.define(
                command.key(),
                command.layer(),
                command.scope(),
                value,
                command.type(),
                validFrom,
                command.validUntil(),
                actor,
                command.justification(),
                now));

    events.publishEvent(
        new ParameterRuleDefined(rule.id(), command.key(), command.layer(), command.scope(), now));
    if (command.layer().isDirective()) {
      events.publishEvent(
          new DirectiveIssued(rule.id(), command.key(), actor, command.justification(), now));
      log.info(
          "DirectiveIssued ruleId={} key={} definedBy={} justification={}",
          rule.id(),
          command.key().value(),
          actor,
          command.justification());
    } else {
      log.info(
          "ParameterRuleDefined ruleId={} key={} layer={} definedBy={}",
          rule.id(),
          command.key().value(),
          command.layer(),
          actor);
    }
    return rule.toView();
  }

  /**
   * Lists rules for audit/curation, optionally filtered by key and/or layer (nulls = no filter).
   */
  @Transactional(readOnly = true)
  public List<ParameterRuleView> listRules(ParameterKey key, ParameterLayer layer) {
    return repository.list(key != null ? key.value() : null, layer).stream()
        .map(ParameterRule::toView)
        .toList();
  }

  // --- MarkupProvider (graduated SPEC-0005 stub, DL-0040) ---

  @Override
  @Transactional(readOnly = true)
  public MarkupDecision currentMarkup(MarkupScope scope) {
    return MarkupDecision.from(resolve(ParameterKey.MARKUP_PCT, scope.toParameterScope()));
  }

  // --- internals ---

  private static void authorize(ParameterLayer layer, Set<String> roles) {
    Set<String> safe = roles != null ? roles : Set.of();
    boolean allowed =
        layer.isDirective()
            ? safe.contains("ROLE_DIRECTOR")
            : safe.contains("ROLE_DIRECTOR") || safe.contains("ROLE_POLICY_ADMIN");
    if (!allowed) {
      throw new PolicyDirectiveForbiddenException();
    }
  }

  private static String validateValue(String value, ParameterValueType type) {
    if (type == null || value == null || !type.isValid(value)) {
      throw new PolicyRuleInvalidException();
    }
    return value.trim();
  }

  private static void validateEffectivity(LocalDate validFrom, LocalDate validUntil) {
    if (validFrom == null || (validUntil != null && validUntil.isBefore(validFrom))) {
      throw new PolicyRuleInvalidException();
    }
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }
}
