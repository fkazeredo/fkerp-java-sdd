package com.fksoft.domain.billing.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Seeded municipal ISS rate (SPEC-0016 BR2; DL-0044): the ISS rate fraction for a municipality.
 * This is system reference data (Flyway-seeded), read by the {@link DbMunicipalIssRateProvider}.
 * The legal ISS band is 2%–5% (LC 116/2003); the default (when a municipality is absent) is the 5%
 * cap. Module-internal.
 */
@Entity
@Table(name = "municipal_iss_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MunicipalIssRate {

  @Id private String municipality;

  private BigDecimal issRate;

  /** The municipality code (IBGE). */
  public String municipality() {
    return municipality;
  }

  /** The ISS rate fraction (e.g. {@code 0.05}). */
  public BigDecimal issRate() {
    return issRate;
  }
}
