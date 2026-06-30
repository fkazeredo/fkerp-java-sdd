package com.fksoft.domain.admin;

/**
 * The kind of administrative supplier (SPEC-0025 BR1): a utility (water/power/telephone), a
 * software vendor, a generic service provider (PJ), or other. This is the <em>administrative</em>
 * supplier — never a tourism brand/supplier (that is the Portfolio, SPEC-0020).
 */
public enum AdminSupplierType {
  UTILITY,
  SOFTWARE,
  SERVICE,
  OTHER
}
