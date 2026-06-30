/**
 * Portfolio module (SPEC-0020): the <strong>representation</strong> context — the brands/suppliers
 * the Acme represents commercially (it is a GSA), the representation contracts that grant that
 * right (validity + the contract document in the Compliance vault, with an expiry alert) and the
 * goals per brand with the realized-vs-goal tracking projected from sales events (redesign Part 5,
 * line 133/ 161). It is a Supporting context, distinct from {@code Assets} (internal patrimony,
 * SPEC-0021) — two separate contexts (DL-0060).
 *
 * <p><strong>RepresentedBrand</strong> ({@link
 * com.fksoft.domain.portfolio.internal.RepresentedBrand}) is the reference "which brand", with a
 * unique {@code brandRef} (value) and an ACTIVE/INACTIVE status (BR1).
 * <strong>RepresentationContract </strong> ({@link
 * com.fksoft.domain.portfolio.internal.RepresentationContract}) holds the validity window, the
 * Compliance {@code documentId} (value, never an FK) and the reference terms (jsonb, not prices —
 * BR6); selling a brand without an in-force contract only <strong>alerts</strong>, it never blocks
 * (BR2/DL-0061), and an expiring contract is signalled once by a controlled-clock job that
 * publishes {@link com.fksoft.domain.portfolio.RepresentationExpiring} (BR5/DL-0063).
 *
 * <p><strong>BrandGoal</strong> ({@link com.fksoft.domain.portfolio.internal.BrandGoal}) defines a
 * VOLUME or REVENUE target per (brand, period); the <strong>realized</strong> is a read-model
 * projection (BR4/DL-0062) built from sales events — {@code BookingConfirmed} (VOLUME) and {@code
 * SpreadRealized} (REVENUE, BRL) — matched to a brand by a Portfolio-owned sale-attribution intake
 * ({@link com.fksoft.domain.portfolio.internal.BrandSaleAttribution}), <strong>without changing the
 * sale event</strong>. The projection ({@link com.fksoft.domain.portfolio.internal.BrandRealized})
 * is idempotent per {@code (metric, sourceRef)}.
 *
 * <p>Spring Modulith application module (the 17th). Dependencies: it consumes the {@code booking}
 * {@code BookingConfirmed} and the {@code reconciliation} {@code ReconciliationCaseOpened}/{@code
 * SpreadRealized} events only (never those modules' facades nor internals — DL-0062), plus the
 * {@code money}/{@code error} kernels; no other business module depends back on Portfolio, so the
 * module graph stays <strong>acyclic</strong> (Spring Modulith verify). The {@code internal}
 * sub-package (the aggregates, the projection and their repositories) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Portfolio")
package com.fksoft.domain.portfolio;
