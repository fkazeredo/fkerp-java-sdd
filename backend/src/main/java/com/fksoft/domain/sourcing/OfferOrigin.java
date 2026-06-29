package com.fksoft.domain.sourcing;

/**
 * Where a sourced offer comes from (SPEC-0009 BR1). The hybrid world (redesign Parte 3.3): own
 * integrated portals, external sites, third-party/physical catalogs and raw demand. External
 * (API/persistence) value is the constant name.
 */
public enum OfferOrigin {

  /** Own integrated portal (API), e.g. Portal de Experiências / Portal de Locação. */
  PORTAL_API,

  /** An external site without integration (price typed/copied in). */
  EXTERNAL_SITE,

  /** A third-party system or physical catalog the ERP never holds structured. */
  THIRD_PARTY_CATALOG,

  /** Raw demand (quote request, WhatsApp, phone). */
  RAW_DEMAND
}
