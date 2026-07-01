package com.fksoft.domain.sourcing;

/**
 * The {@code OFFER_ORIGIN} code constant the domain wires (SPEC-0031 BR5; DL-0117). After {@code
 * OfferOrigin} became an editable cadastro, the one wired value is preserved: the inbound ACL
 * records a sourced offer's provenance as {@link #EXTERNAL_SITE} when it creates the INTEGRATED
 * quote (SPEC-0009 BR2). The remaining origins (portal, third-party catalog, raw demand) are pure
 * reference data — seeded in the cadastro, chosen by the operator, never branched on. The cadastro
 * owns the extensible set + labels; this class owns only the wired value.
 */
public final class OfferOriginCodes {

  /** An external site without integration — the provenance the inbound ACL records. */
  public static final String EXTERNAL_SITE = "EXTERNAL_SITE";

  private OfferOriginCodes() {}
}
