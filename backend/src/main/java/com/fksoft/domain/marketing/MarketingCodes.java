package com.fksoft.domain.marketing;

import java.util.List;

/**
 * The small set of Marketing reference-data code constants whose behavior the domain wires
 * (SPEC-0031 BR5; DL-0116). After {@code ConsentPurpose} and {@code SubjectType} became editable
 * cadastros, the values the domain branches on are preserved here as code constants — the cadastro
 * owns the extensible set + labels, this class owns the wired behavior.
 *
 * <ul>
 *   <li>{@link #NEWSLETTER} is the purpose the send filter / preview / candidate base default to
 *       (BR2/BR3, DL-0059) — the only purpose wired in v1.
 *   <li>{@link #KNOWN_PURPOSES} is the seeded purpose set the LGPD erasure sweeps over (BR6,
 *       DL-0058) instead of iterating a compiled {@code enum.values()}; a new purpose added as a
 *       cadastro item flows as pure data and does not need wiring here.
 *   <li>{@link #ACCOUNT}/{@link #AGENT} are the seeded subject-type codes. Nothing branches on them
 *       today (they are a persisted/query value), so they are here only as the documented set.
 * </ul>
 */
public final class MarketingCodes {

  /** The newsletter consent purpose (the v1 purpose the send filter defaults to). */
  public static final String NEWSLETTER = "NEWSLETTER";

  /** A commercial account (agency) subject. */
  public static final String ACCOUNT = "ACCOUNT";

  /** An individual agent subject. */
  public static final String AGENT = "AGENT";

  /**
   * The consent purposes the LGPD erasure sweeps over to write a revocation tombstone (BR6). The
   * seeded set; a purpose added later as a cadastro item is pure data (no wired erasure branch is
   * needed — the sweep still anonymizes all of a subject's rows by id).
   */
  public static final List<String> KNOWN_PURPOSES = List.of(NEWSLETTER);

  private MarketingCodes() {}
}
