package com.fksoft.domain.quoting;

import com.fksoft.domain.accounts.AccountDirectory;
import com.fksoft.domain.commercialpolicy.MarkupDecision;
import com.fksoft.domain.commercialpolicy.MarkupProvider;
import com.fksoft.domain.commissioning.CommissionCalculator;
import com.fksoft.domain.commissioning.CommissionInput;
import com.fksoft.domain.commissioning.CommissionStatement;
import com.fksoft.domain.exchange.ExchangeRateProvider;
import com.fksoft.domain.exchange.PinnedSellRateView;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.internal.OverrideRecord;
import com.fksoft.domain.quoting.internal.Quote;
import com.fksoft.domain.quoting.internal.QuoteComposition;
import com.fksoft.domain.quoting.internal.QuoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the quoting module (SPEC-0005). Composes a MANUAL quote from the
 * Accounts/Exchange/Commissioning/CommercialPolicy facades, freezing the whole provenance (BR4),
 * and applies overrides that always record the divergence (BR6/BR8). Implements the {@link
 * QuoteDirectory} port for Booking and Reconciliation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService implements QuoteDirectory, QuoteIntegrationPort {

  private final QuoteRepository repository;
  private final AccountDirectory accountDirectory;
  private final ExchangeRateProvider exchangeRateProvider;
  private final CommissionCalculator commissionCalculator;
  private final MarkupProvider markupProvider;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Composes a MANUAL quote: validates the account (BR2) and the prevailing rate (BR3), converts
   * the base to the sale currency, derives the two-sided commission and the markup, and freezes the
   * suggestion (DL-0009). {@code appliedAmount} starts equal to {@code suggestedAmount}.
   *
   * @param command the composition input
   * @param actor who composes it (audit)
   * @return the composed quote view
   * @throws QuoteAccountNotFoundException when the account does not exist (BR2)
   * @throws QuoteRateMissingException when no rate is in effect for the pair (BR3)
   */
  @Transactional
  public QuoteView compose(ComposeQuoteCommand command, String actor) {
    if (!accountDirectory.exists(command.accountId())) {
      throw new QuoteAccountNotFoundException();
    }
    PinnedSellRateView rate =
        exchangeRateProvider
            .currentRate(command.currencyPair())
            .orElseThrow(QuoteRateMissingException::new);

    String saleCurrency = command.currencyPair().quote();
    Money baseConverted =
        Money.of(command.basePrice().amount().multiply(rate.rate()), saleCurrency);

    CommissionStatement commission =
        commissionCalculator.compute(
            new CommissionInput(
                baseConverted, command.supplierCommissionPct(), command.agentCommissionPct()));

    MarkupDecision markup = markupProvider.currentMarkup();
    Money markupAmount = baseConverted.multiply(markup.pct());
    Money suggested = baseConverted.add(markupAmount);

    QuoteComposition composition =
        new QuoteComposition(
            PriceOrigin.MANUAL,
            command.basePrice(),
            command.currencyPair(),
            rate.rate(),
            rate.id(),
            baseConverted,
            command.supplierCommissionPct(),
            command.agentCommissionPct(),
            commission.supplierCommission(),
            commission.agentCommission(),
            commission.spread(),
            commission.spreadNegative(),
            markup.pct(),
            markupAmount,
            markup.source(),
            suggested);

    Quote quote =
        repository.save(
            Quote.compose(
                command.accountId(), composition, command.validUntil(), clock.instant(), actor));

    events.publishEvent(
        new QuoteComposed(quote.id(), quote.accountId(), suggested, quote.createdAt()));
    log.info(
        "QuoteComposed quoteId={} accountId={} suggested={}",
        quote.id(),
        quote.accountId(),
        suggested.amount());
    return quote.toView();
  }

  /**
   * Creates an INTEGRATED quote from a trusted, closed external price (SPEC-0009 BR2, DL-0018). The
   * suggestion engine does not run and no override is created: {@code suggestedAmount ==
   * appliedAmount == externalPrice}. This is the {@link QuoteIntegrationPort} the Sourcing ACL
   * calls — only domain values cross the boundary (BR6).
   */
  @Override
  @Transactional
  public UUID createIntegratedQuote(
      UUID accountId, UUID sourceOfferId, Money externalPrice, Instant validUntil, String actor) {
    Quote quote =
        repository.save(
            Quote.composeIntegrated(
                accountId, sourceOfferId, externalPrice, validUntil, clock.instant(), actor));
    events.publishEvent(
        new QuoteComposed(quote.id(), quote.accountId(), externalPrice, quote.createdAt()));
    log.info(
        "QuoteComposed quoteId={} accountId={} priceOrigin=INTEGRATED applied={}",
        quote.id(),
        quote.accountId(),
        externalPrice.amount());
    return quote.id();
  }

  /**
   * Applies a price override with a mandatory reason (BR6); records the divergence and updates
   * {@code appliedAmount} (the suggestion is immutable, BR5).
   *
   * @param quoteId the quote to override
   * @param newApplied the new applied amount (sale currency — BR7)
   * @param reason the non-empty reason
   * @param performedBy who performs the override
   * @return the updated quote view
   * @throws QuoteNotFoundException when the quote does not exist
   */
  @Transactional
  public QuoteView override(UUID quoteId, Money newApplied, String reason, String performedBy) {
    Quote quote = repository.findById(quoteId).orElseThrow(QuoteNotFoundException::new);
    quote.applyOverride(newApplied, reason, performedBy, clock.instant());
    repository.save(quote);

    List<OverrideRecord> records = quote.overrides();
    OverrideRecord last = records.get(records.size() - 1);
    events.publishEvent(
        new PriceOverridden(
            quote.id(),
            Money.of(last.fromAmount(), newApplied.currency()),
            newApplied,
            last.reason(),
            performedBy,
            last.performedAt()));
    log.info(
        "PriceOverridden quoteId={} from={} to={} performedBy={}",
        quote.id(),
        last.fromAmount(),
        last.toAmount(),
        performedBy);
    return quote.toView();
  }

  /**
   * Fetches a quote by id (with its overrides).
   *
   * @throws QuoteNotFoundException when the quote does not exist
   */
  @Transactional(readOnly = true)
  public QuoteView getById(UUID quoteId) {
    return repository.findById(quoteId).map(Quote::toView).orElseThrow(QuoteNotFoundException::new);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<QuoteSnapshot> find(UUID quoteId) {
    return repository.findById(quoteId).map(Quote::toSnapshot);
  }
}
