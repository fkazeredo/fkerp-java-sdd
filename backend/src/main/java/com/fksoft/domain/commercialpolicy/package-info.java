/**
 * CommercialPolicy module — governed parameters and the precedence engine (SPEC-0014; graduates the
 * SPEC-0005 markup seam).
 *
 * <p>Centralizes the operation's <strong>governed parameters</strong> (markup, drift limit,
 * reconciliation tolerance — <em>not</em> product prices) as {@code ParameterRule}s organized by
 * <strong>layer</strong> ({@link com.fksoft.domain.commercialpolicy.ParameterLayer}: Directive &gt;
 * Promotion &gt; Contract &gt; Policy &gt; SystemDefault), <strong>scope</strong> (global / account
 * / product / channel) and <strong>effectivity</strong>. The {@link
 * com.fksoft.domain.commercialpolicy.CommercialPolicyService#resolve} engine returns the highest-
 * precedence applicable value together with its {@link
 * com.fksoft.domain.commercialpolicy.Provenance} (which layer won, who defined it, when) — BR2/BR3.
 *
 * <p>It also implements the {@link com.fksoft.domain.commercialpolicy.MarkupProvider} port consumed
 * by Quoting: the Phase-1 stub is now graduated into the real engine, so the markup that flows into
 * a Quote carries the winning layer as its source instead of always {@code SYSTEM_DEFAULT}. Other
 * contexts read parameters as Open-Host (resolution is pure, BR6). Spring Modulith application
 * module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "CommercialPolicy")
package com.fksoft.domain.commercialpolicy;
