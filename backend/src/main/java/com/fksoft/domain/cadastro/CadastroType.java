package com.fksoft.domain.cadastro;

/**
 * The catalogue of convertible reference-data types (SPEC-0031; ADR-0019/DL-0115) — the set of
 * business enums that became editable cadastros. This is a <strong>technical key</strong> of the
 * {@code cadastro_item} registry, not itself reference data: it identifies which cadastro a {@code
 * code} belongs to. New types are added here (and seeded in a Flyway migration) as later slices
 * (18b–18d) convert more groups.
 *
 * <p>Slice 18a converts the Admin, Assets and Billing groups. Adding a value here is additive and
 * requires a matching migration seed; it never removes a value (that would orphan persisted codes).
 */
public enum CadastroType {

  /** Admin recurring-expense kind (was {@code AdminExpenseKind}; branches to {@code EntryType}). */
  ADMIN_EXPENSE_KIND,

  /** Admin contract recurrence cadence (was {@code AdminRecurrence}). */
  ADMIN_RECURRENCE,

  /** Admin supplier type (was {@code AdminSupplierType}). */
  ADMIN_SUPPLIER_TYPE,

  /** Internal asset type (was {@code AssetType}; {@code SOFTWARE_LICENSE} requires an expiry). */
  ASSET_TYPE,

  /** Billing tax withholding kind (was {@code WithholdingKind}). */
  WITHHOLDING_KIND,

  /** Billing issuer tax regime (was {@code TaxRegime}; selects the {@code TaxRegimeStrategy}). */
  TAX_REGIME,

  // --- Slice 18b: Marketing / Intelligence / Portfolio (DL-0116) ---

  /** Marketing consent purpose (was {@code ConsentPurpose}; NEWSLETTER is the wired default). */
  CONSENT_PURPOSE,

  /** Marketing consent/campaign subject type (was {@code SubjectType}). */
  MARKETING_SUBJECT_TYPE,

  /** Intelligence insight axis (was {@code SubjectKind}; AGENCY is produced in v1). */
  INSIGHT_SUBJECT_KIND,

  /** Intelligence insight type (was {@code InsightType}). */
  INSIGHT_TYPE,

  /**
   * Intelligence advisor verdict (was {@code Verdict}; CONVERTE/QUEIMA_MARGEM drive the guardrail).
   */
  INSIGHT_VERDICT,

  /** Portfolio brand-goal metric (was {@code GoalMetric}; VOLUME/REVENUE drive the projection). */
  GOAL_METRIC,

  // --- Slice 18c: Sourcing / Exchange / Booking / Compliance (DL-0117) ---

  /** Sourcing offer origin (was {@code OfferOrigin}; provenance of a sourced offer). */
  OFFER_ORIGIN,

  /**
   * Sourcing integration level (was {@code IntegrationLevel}; INBOUND gates the INTEGRATED quoting
   * branch — DL-0018).
   */
  INTEGRATION_LEVEL,

  /** Exchange market-rate source (was {@code MarketRateSource}; FEED/MANUAL — DL-0025). */
  MARKET_RATE_SOURCE,

  /**
   * Booking cancellation/no-show charge kind (was {@code ChargeKind}; drives the AP/AR posting).
   */
  CHARGE_KIND,

  /**
   * Booking cancellation type (was {@code CancellationType}; STANDARD/ALL_SALES_FINAL/CUSTOM drive
   * the penalty windows + the merchant trap — DL-0024/DL-0010).
   */
  CANCELLATION_TYPE,

  /** Compliance document type (was {@code DocumentType}; drives retention + requirements). */
  DOCUMENT_TYPE,

  /** Compliance signed-document format (was {@code SignedFormat}; CAdES/XAdES/PAdES). */
  SIGNED_FORMAT,

  /**
   * Compliance requirement phase (was {@code RequirementPhase}; AT_REGISTRATION drives the
   * close-check — DL-0012).
   */
  REQUIREMENT_PHASE
}
