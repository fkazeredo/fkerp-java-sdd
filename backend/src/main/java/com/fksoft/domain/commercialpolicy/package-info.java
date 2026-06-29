/**
 * CommercialPolicy module — markup seam (SPEC-0005, graduated by SPEC-0014).
 *
 * <p>In Phase 1 this module is a <strong>traceable stub</strong>: it exposes a {@link
 * com.fksoft.domain.commercialpolicy.MarkupProvider} port that returns a single governed default
 * markup (source {@code SYSTEM_DEFAULT}). The real precedence engine (Directive &gt; Promotion &gt;
 * Contract &gt; Policy &gt; Default) belongs to SPEC-0014; until then this stand-in lets Quoting
 * compose prices without inventing fake policy logic (simulation-and-mocking.md). Spring Modulith
 * application module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "CommercialPolicy")
package com.fksoft.domain.commercialpolicy;
