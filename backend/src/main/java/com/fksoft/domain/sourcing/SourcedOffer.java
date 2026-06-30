package com.fksoft.domain.sourcing;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SourcedOffer aggregate (SPEC-0009): the provenance of an offer — free-text product, base price,
 * origin and integration level, plus an optional external reference. Free text is a valid offer
 * (BR1); it does not require a structured catalog. Module-internal.
 */
@Entity
@Table(name = "sourced_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class SourcedOffer {

  @Id private UUID id;

  @Column(name = "product_text")
  private String productText;

  private BigDecimal baseAmount;
  private String baseCurrency;

  @Enumerated(EnumType.STRING)
  private OfferOrigin origin;

  @Enumerated(EnumType.STRING)
  private IntegrationLevel integrationLevel;

  private String externalRef;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new sourced offer, enforcing the non-empty product text invariant (BR1).
   *
   * @param productText free-text product description (non-empty)
   * @param basePrice the base price in the supplier's currency
   * @param origin where the offer comes from
   * @param integrationLevel how integrated the source is
   * @param externalRef optional external reference (e.g. an external quotation id)
   * @param now creation instant (UTC)
   * @param actor who registered it (audit)
   * @return a new, persistable sourced offer
   * @throws SourcedOfferInvalidException when the product text is blank (BR1)
   */
  public static SourcedOffer register(
      String productText,
      Money basePrice,
      OfferOrigin origin,
      IntegrationLevel integrationLevel,
      String externalRef,
      Instant now,
      String actor) {
    if (productText == null || productText.isBlank()) {
      throw new SourcedOfferInvalidException();
    }
    SourcedOffer offer = new SourcedOffer();
    offer.id = UUID.randomUUID();
    offer.productText = productText.trim();
    offer.baseAmount = basePrice.amount();
    offer.baseCurrency = basePrice.currency();
    offer.origin = origin;
    offer.integrationLevel = integrationLevel;
    offer.externalRef = externalRef;
    offer.createdAt = now;
    offer.updatedAt = now;
    offer.createdBy = actor;
    offer.updatedBy = actor;
    return offer;
  }

  /** Projects this aggregate to its public read view. */
  public SourcedOfferView toView() {
    return new SourcedOfferView(
        id,
        productText,
        Money.of(baseAmount, baseCurrency),
        origin,
        integrationLevel,
        externalRef,
        createdAt);
  }
}
