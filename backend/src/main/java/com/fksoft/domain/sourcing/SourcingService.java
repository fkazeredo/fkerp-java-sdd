package com.fksoft.domain.sourcing;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.sourcing.internal.SourcedOffer;
import com.fksoft.domain.sourcing.internal.SourcedOfferRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the sourcing module (SPEC-0009): registers and fetches sourced offers
 * (the provenance of an offer). The inbound ACL path (creating a Quote INTEGRATED from a translated
 * webhook command) is added in Slice 8c. The audit actor is resolved by the delivery layer and
 * passed in, keeping the domain free of infra dependencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourcingService {

  private final SourcedOfferRepository offerRepository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Registers a sourced offer, enforcing the non-empty product text invariant (BR1) and emitting
   * {@link OfferSourced}.
   *
   * @param productText free-text product description (non-empty)
   * @param basePrice base price in the supplier's currency
   * @param origin where the offer comes from
   * @param integrationLevel how integrated the source is
   * @param externalRef optional external reference
   * @param actor who registers it (audit)
   * @return the registered offer view
   * @throws SourcedOfferInvalidException when the product text is blank (BR1)
   */
  @Transactional
  public SourcedOfferView register(
      String productText,
      Money basePrice,
      OfferOrigin origin,
      IntegrationLevel integrationLevel,
      String externalRef,
      String actor) {
    SourcedOffer offer =
        offerRepository.save(
            SourcedOffer.register(
                productText,
                basePrice,
                origin,
                integrationLevel,
                externalRef,
                clock.instant(),
                actor));
    events.publishEvent(
        new OfferSourced(offer.id(), offer.origin(), offer.integrationLevel(), offer.createdAt()));
    log.info(
        "OfferSourced offerId={} origin={} integrationLevel={}",
        offer.id(),
        offer.origin(),
        offer.integrationLevel());
    return offer.toView();
  }

  /**
   * Fetches a sourced offer by id.
   *
   * @throws SourcedOfferNotFoundException when no offer has that id
   */
  @Transactional(readOnly = true)
  public SourcedOfferView getById(UUID id) {
    return offerRepository
        .findById(id)
        .map(SourcedOffer::toView)
        .orElseThrow(SourcedOfferNotFoundException::new);
  }
}
