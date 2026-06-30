/**
 * Intelligence (DSS) module (SPEC-0013): the prescriptive brain of the ERP. It listens to events
 * from all contexts (read-only) and produces {@code Insight}s — evidence (number + provenance) +
 * recommendation (action with gain/risk) + guardrail (alert on crossing a limit, without blocking).
 * The golden rule of the redesign (Part 8): it <strong>only reads events, advises, and NEVER
 * commands</strong> (BR2/BR3).
 *
 * <p>Spring Modulith application module and a deliberate <strong>consumer-leaf</strong>: nobody
 * depends on it, and it never calls back into a producer (it learns the {@code booking → account}
 * mapping from {@code BookingConfirmed}, it does not fetch it from Booking — DL-0034). The v1 ships
 * the Insight framework and two direct-profit insights: {@link
 * com.fksoft.domain.intelligence.PromoFxAdvisor} (does the FX freeze promo pay for itself, by
 * agency) and the {@code OverrideNudge} seam (gated behind a feature flag until the commission-tier
 * model exists — BR6/DL-0036).
 *
 * <p>The advisors are <strong>deterministic, rule-based</strong> (Rule Zero / phase DESIGN
 * GUIDANCE): no LLM/ML dependency. The {@link com.fksoft.domain.intelligence.InsightNarrator} port
 * (default {@code RuleBasedInsightNarrator}) is the seam where a real predictive narrator could
 * plug in later, behind an ACL, with a deterministic test stub — it is not wired to any live model.
 *
 * <p>Public API: {@link com.fksoft.domain.intelligence.IntelligenceService}, the views, the value
 * objects ({@link com.fksoft.domain.intelligence.PromoFxAdvisor} and its inputs/outputs), the
 * events ({@link com.fksoft.domain.intelligence.InsightGenerated}, {@link
 * com.fksoft.domain.intelligence.InsightDecided}), the {@link
 * com.fksoft.domain.intelligence.InsightNarrator} port and the business exceptions ({@link
 * com.fksoft.domain.intelligence.InsightNotFoundException}, {@link
 * com.fksoft.domain.intelligence.InsightDecisionInvalidException}). The implementation types
 * (entities, repositories, the event listeners, the rule-based narrator) live in this same package
 * marked {@link com.fksoft.domain.ModuleInternal} and must never be reached from other modules —
 * encapsulation is enforced by ArchUnit (Phase 9 / ADR 0016).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Intelligence")
package com.fksoft.domain.intelligence;
