package com.fksoft.domain.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link SourcedOffer} aggregate invariants (SPEC-0009 BR1). */
class SourcedOfferTest {

  private static final Money PRICE = Money.of(new BigDecimal("480.00"), "BRL");

  @Test
  void registersAFreeTextOfferWithProvenance() {
    SourcedOffer offer =
        SourcedOffer.register(
            "City Tour Rio - full day",
            PRICE,
            OfferOrigin.EXTERNAL_SITE,
            IntegrationLevel.INBOUND,
            "QS-2026-555",
            Instant.parse("2026-06-29T10:00:00Z"),
            "operador1");

    assertThat(offer.id()).isNotNull();
    assertThat(offer.productText()).isEqualTo("City Tour Rio - full day");
    assertThat(offer.toView().basePrice()).isEqualTo(PRICE);
    assertThat(offer.origin()).isEqualTo(OfferOrigin.EXTERNAL_SITE);
    assertThat(offer.integrationLevel()).isEqualTo(IntegrationLevel.INBOUND);
    assertThat(offer.externalRef()).isEqualTo("QS-2026-555");
  }

  @Test
  void trimsProductTextAndAcceptsNullExternalRef() {
    SourcedOffer offer =
        SourcedOffer.register(
            "  Passeio de barco  ",
            PRICE,
            OfferOrigin.RAW_DEMAND,
            IntegrationLevel.NONE,
            null,
            Instant.parse("2026-06-29T10:00:00Z"),
            "operador1");

    assertThat(offer.productText()).isEqualTo("Passeio de barco");
    assertThat(offer.externalRef()).isNull();
  }

  @Test
  void rejectsBlankProductText() {
    assertThatThrownBy(
            () ->
                SourcedOffer.register(
                    "   ",
                    PRICE,
                    OfferOrigin.EXTERNAL_SITE,
                    IntegrationLevel.INBOUND,
                    null,
                    Instant.parse("2026-06-29T10:00:00Z"),
                    "operador1"))
        .isInstanceOf(SourcedOfferInvalidException.class);
  }
}
