package com.fksoft.infra.web;

import static java.util.Map.entry;

import com.fksoft.domain.accounts.AccountDocumentDuplicateException;
import com.fksoft.domain.accounts.AccountDocumentInvalidException;
import com.fksoft.domain.accounts.AccountNotFoundException;
import com.fksoft.domain.aftersales.SupportCaseInvalidException;
import com.fksoft.domain.aftersales.SupportCaseNotFoundException;
import com.fksoft.domain.aftersales.SupportCaseRefundDuplicateException;
import com.fksoft.domain.aftersales.SupportCaseTransitionInvalidException;
import com.fksoft.domain.assets.AssetAlreadyRetiredException;
import com.fksoft.domain.assets.AssetInvalidException;
import com.fksoft.domain.assets.AssetNotFoundException;
import com.fksoft.domain.assets.LicenseExpiryRequiredException;
import com.fksoft.domain.billing.BillingBaseInvalidException;
import com.fksoft.domain.billing.BillingInvoiceNotFoundException;
import com.fksoft.domain.billing.BillingInvoiceTransitionInvalidException;
import com.fksoft.domain.billing.BillingMunicipalityRejectedException;
import com.fksoft.domain.billing.BillingNfseWebserviceException;
import com.fksoft.domain.booking.BookingLocatorDuplicateException;
import com.fksoft.domain.booking.BookingLocatorInvalidException;
import com.fksoft.domain.booking.BookingNotFoundException;
import com.fksoft.domain.booking.BookingQuoteNotFoundException;
import com.fksoft.domain.booking.BookingTransitionInvalidException;
import com.fksoft.domain.booking.CancellationPolicyInvalidException;
import com.fksoft.domain.commercialpolicy.PolicyDirectiveForbiddenException;
import com.fksoft.domain.commercialpolicy.PolicyParameterUnknownException;
import com.fksoft.domain.commercialpolicy.PolicyRuleInvalidException;
import com.fksoft.domain.commissioning.CommissionBaseInvalidException;
import com.fksoft.domain.commissioning.CommissionPctInvalidException;
import com.fksoft.domain.compliance.ComplianceDocumentNotFoundException;
import com.fksoft.domain.compliance.ComplianceRetentionNotExpiredException;
import com.fksoft.domain.compliance.ComplianceUploadInvalidException;
import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.exchange.ExchangeCurrencyPairInvalidException;
import com.fksoft.domain.exchange.ExchangeMarketRateNotFoundException;
import com.fksoft.domain.exchange.ExchangePeriodInvalidException;
import com.fksoft.domain.exchange.ExchangePositionNotFoundException;
import com.fksoft.domain.exchange.ExchangeRateInvalidException;
import com.fksoft.domain.exchange.ExchangeRateNotFoundException;
import com.fksoft.domain.finance.FinanceEntryNotFoundException;
import com.fksoft.domain.finance.FinanceEntryTransitionInvalidException;
import com.fksoft.domain.finance.FinancePartyInvalidException;
import com.fksoft.domain.finance.FinancePeriodCannotCloseException;
import com.fksoft.domain.finance.FinancePeriodClosedException;
import com.fksoft.domain.finance.FinancePeriodInvalidException;
import com.fksoft.domain.intelligence.InsightDecisionInvalidException;
import com.fksoft.domain.intelligence.InsightNotFoundException;
import com.fksoft.domain.marketing.CampaignInvalidException;
import com.fksoft.domain.marketing.CampaignNotFoundException;
import com.fksoft.domain.marketing.ConsentInvalidException;
import com.fksoft.domain.marketing.ConsentNotFoundException;
import com.fksoft.domain.marketing.NewsletterException;
import com.fksoft.domain.marketing.SegmentInvalidException;
import com.fksoft.domain.payout.PayoutAlreadyExecutedException;
import com.fksoft.domain.payout.PayoutAmountInvalidException;
import com.fksoft.domain.payout.PayoutGatewayException;
import com.fksoft.domain.payout.PayoutNotFoundException;
import com.fksoft.domain.payout.PayoutPayeeInvalidException;
import com.fksoft.domain.payout.PayoutRefundOriginRequiredException;
import com.fksoft.domain.payout.PayoutTransitionInvalidException;
import com.fksoft.domain.payout.PayoutWebhookSignatureInvalidException;
import com.fksoft.domain.people.EmployeeDuplicateException;
import com.fksoft.domain.people.EmployeeInvalidException;
import com.fksoft.domain.people.EmployeeNotFoundException;
import com.fksoft.domain.people.PointAfdInvalidException;
import com.fksoft.domain.people.PointSnapshotInvalidException;
import com.fksoft.domain.people.PointSnapshotNotFoundException;
import com.fksoft.domain.portfolio.BrandDuplicateException;
import com.fksoft.domain.portfolio.BrandGoalInvalidException;
import com.fksoft.domain.portfolio.BrandInvalidException;
import com.fksoft.domain.portfolio.BrandNotFoundException;
import com.fksoft.domain.portfolio.RepresentationContractInvalidException;
import com.fksoft.domain.quoting.QuoteAccountNotFoundException;
import com.fksoft.domain.quoting.QuoteNotFoundException;
import com.fksoft.domain.quoting.QuoteOverrideCurrencyMismatchException;
import com.fksoft.domain.quoting.QuoteOverrideNotApplicableException;
import com.fksoft.domain.quoting.QuoteOverrideReasonRequiredException;
import com.fksoft.domain.quoting.QuoteRateMissingException;
import com.fksoft.domain.reconciliation.ReconciliationCaseNotFoundException;
import com.fksoft.domain.reconciliation.ReconciliationCurrencyMismatchException;
import com.fksoft.domain.sourcing.IntegrationAccountNotFoundException;
import com.fksoft.domain.sourcing.IntegrationPayloadInvalidException;
import com.fksoft.domain.sourcing.IntegrationSignatureInvalidException;
import com.fksoft.domain.sourcing.SourcedOfferInvalidException;
import com.fksoft.domain.sourcing.SourcedOfferNotFoundException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Registry that maps each {@link DomainException} subtype to the HTTP status the presentation layer
 * should return (ADR 0011). Keeping the mapping here — not in the domain — keeps domain exceptions
 * free of transport concerns.
 *
 * <p>Any unmapped {@code DomainException} defaults to {@code 422 Unprocessable Entity}. A
 * build-time test ({@code HttpErrorMappingCompletenessTest}) fails if a {@code DomainException}
 * subtype is ever left unmapped, so the default can never hide a forgotten entry.
 */
@Component
public class HttpErrorMapping {

  private final Map<Class<? extends DomainException>, HttpStatus> mapping =
      Map.ofEntries(
          entry(AccountDocumentInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(AccountDocumentDuplicateException.class, HttpStatus.CONFLICT),
          entry(AccountNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ExchangeCurrencyPairInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ExchangeRateInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ExchangeRateNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ExchangeMarketRateNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ExchangePositionNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ExchangePeriodInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(CommissionPctInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(CommissionBaseInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(QuoteAccountNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(QuoteRateMissingException.class, HttpStatus.UNPROCESSABLE_ENTITY),
          entry(QuoteNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(QuoteOverrideReasonRequiredException.class, HttpStatus.BAD_REQUEST),
          entry(QuoteOverrideCurrencyMismatchException.class, HttpStatus.BAD_REQUEST),
          entry(QuoteOverrideNotApplicableException.class, HttpStatus.CONFLICT),
          entry(BookingQuoteNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(BookingNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(BookingTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(BookingLocatorDuplicateException.class, HttpStatus.CONFLICT),
          entry(BookingLocatorInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(CancellationPolicyInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ReconciliationCaseNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ReconciliationCurrencyMismatchException.class, HttpStatus.BAD_REQUEST),
          entry(FinancePartyInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(FinancePeriodInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(FinanceEntryNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(FinanceEntryTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(FinancePeriodClosedException.class, HttpStatus.CONFLICT),
          entry(FinancePeriodCannotCloseException.class, HttpStatus.CONFLICT),
          entry(ComplianceDocumentNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ComplianceRetentionNotExpiredException.class, HttpStatus.CONFLICT),
          entry(ComplianceUploadInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(SourcedOfferInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(SourcedOfferNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(IntegrationSignatureInvalidException.class, HttpStatus.UNAUTHORIZED),
          entry(IntegrationPayloadInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(IntegrationAccountNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY),
          entry(PointSnapshotNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(PointSnapshotInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(PointAfdInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(EmployeeNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(EmployeeDuplicateException.class, HttpStatus.CONFLICT),
          entry(EmployeeInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(InsightNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(InsightDecisionInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(PolicyParameterUnknownException.class, HttpStatus.NOT_FOUND),
          entry(PolicyRuleInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(PolicyDirectiveForbiddenException.class, HttpStatus.FORBIDDEN),
          entry(BillingInvoiceNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(BillingInvoiceTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(BillingBaseInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(BillingMunicipalityRejectedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
          entry(BillingNfseWebserviceException.class, HttpStatus.BAD_GATEWAY),
          entry(PayoutNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(PayoutPayeeInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(PayoutAmountInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(PayoutRefundOriginRequiredException.class, HttpStatus.BAD_REQUEST),
          entry(PayoutAlreadyExecutedException.class, HttpStatus.CONFLICT),
          entry(PayoutTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(PayoutGatewayException.class, HttpStatus.BAD_GATEWAY),
          entry(PayoutWebhookSignatureInvalidException.class, HttpStatus.UNAUTHORIZED),
          entry(SupportCaseNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(SupportCaseInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(SupportCaseTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(SupportCaseRefundDuplicateException.class, HttpStatus.CONFLICT),
          entry(ConsentInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ConsentNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(SegmentInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(CampaignInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(CampaignNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(NewsletterException.class, HttpStatus.BAD_GATEWAY),
          entry(BrandNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(BrandDuplicateException.class, HttpStatus.CONFLICT),
          entry(BrandInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(RepresentationContractInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(BrandGoalInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(AssetNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(AssetInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(LicenseExpiryRequiredException.class, HttpStatus.BAD_REQUEST),
          entry(AssetAlreadyRetiredException.class, HttpStatus.CONFLICT));

  /** The HTTP status for a domain exception type; {@code 422} when unmapped. */
  public HttpStatus statusFor(Class<? extends DomainException> type) {
    return mapping.getOrDefault(type, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /** The set of explicitly mapped exception types (used by the completeness test). */
  public Set<Class<? extends DomainException>> mappedTypes() {
    return mapping.keySet();
  }
}
