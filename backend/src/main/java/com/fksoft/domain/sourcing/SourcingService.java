package com.fksoft.domain.sourcing;

import com.fksoft.domain.accounts.AccountDirectory;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteIntegrationPort;
import com.fksoft.domain.sourcing.internal.InboundQuotation;
import com.fksoft.domain.sourcing.internal.InboundQuotationRepository;
import com.fksoft.domain.sourcing.internal.SourcedOffer;
import com.fksoft.domain.sourcing.internal.SourcedOfferRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the sourcing module (SPEC-0009): registers/fetches sourced offers (the
 * provenance of an offer) and owns the application side of the inbound ACL — it turns a translated,
 * signature-verified inbound command into a Quote INTEGRATED via the Quoting facade, idempotently
 * per {@code externalQuotationId} (BR4). The external vendor payload never reaches here (BR6): only
 * {@link RegisterInboundQuotationCommand} crosses the boundary. The audit actor is resolved by the
 * delivery layer and passed in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourcingService {

  /** The connector name surfaced in observability and the health view. */
  public static final String CONNECTOR = "quotation-site";

  private final SourcedOfferRepository offerRepository;
  private final InboundQuotationRepository inboundRepository;
  private final AccountDirectory accountDirectory;
  private final QuoteIntegrationPort quoteIntegrationPort;
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

  /**
   * Processes a translated, signature-verified inbound quotation (SPEC-0009 BR2/BR4/BR6, DL-0017,
   * DL-0018): idempotent by {@code externalQuotationId} — a re-delivery returns the same result
   * without creating a duplicate; resolves the account by document (rejecting when unknown);
   * registers a {@link SourcedOffer} for provenance ({@code EXTERNAL_SITE}/{@code INBOUND}); and
   * creates a Quote INTEGRATED through the Quoting facade (no recomposition). Emits {@link
   * IntegratedQuoteCreated}.
   *
   * @param command the translated inbound command (the only shape that crosses the ACL boundary)
   * @param actor who/what is processing it (audit; e.g. the connector)
   * @return the created (or, on a re-delivery, the existing) INTEGRATED quote result
   * @throws IntegrationAccountNotFoundException when no account matches the document (DL-0017)
   */
  @Transactional
  public InboundQuotationResult processInbound(
      RegisterInboundQuotationCommand command, String actor) {
    Optional<InboundQuotation> existing = inboundRepository.findById(command.externalQuotationId());
    if (existing.isPresent()) {
      // BR4: re-delivery of the same id returns the same quote (idempotent), nothing new is
      // created.
      log.info(
          "InboundQuotation duplicate connector={} externalQuotationId={} class={}",
          CONNECTOR,
          command.externalQuotationId(),
          IntegrationFailureClass.DUPLICATE);
      return new InboundQuotationResult(existing.get().quoteId(), "INTEGRATED", command.price());
    }

    UUID accountId =
        accountDirectory
            .findIdByDocument(command.accountDocument())
            .orElseThrow(IntegrationAccountNotFoundException::new);

    SourcedOffer offer =
        offerRepository.save(
            SourcedOffer.register(
                command.productText(),
                command.price(),
                OfferOrigin.EXTERNAL_SITE,
                IntegrationLevel.INBOUND,
                command.externalQuotationId(),
                clock.instant(),
                actor));
    events.publishEvent(
        new OfferSourced(offer.id(), offer.origin(), offer.integrationLevel(), offer.createdAt()));

    UUID quoteId =
        quoteIntegrationPort.createIntegratedQuote(
            accountId, offer.id(), command.price(), null, actor);

    Instant now = clock.instant();
    inboundRepository.save(
        InboundQuotation.of(command.externalQuotationId(), quoteId, accountId, now));
    events.publishEvent(new IntegratedQuoteCreated(quoteId, command.externalQuotationId(), now));
    log.info(
        "IntegratedQuoteCreated connector={} externalQuotationId={} quoteId={}",
        CONNECTOR,
        command.externalQuotationId(),
        quoteId);
    return new InboundQuotationResult(quoteId, "INTEGRATED", command.price());
  }

  /** The connector health read-model (SPEC-0009 Observability; the connector is observed here). */
  @Transactional(readOnly = true)
  public ConnectorHealthView connectorHealth() {
    return new ConnectorHealthView(
        CONNECTOR, "UP", inboundRepository.count(), inboundRepository.findLastReceivedAt());
  }
}
