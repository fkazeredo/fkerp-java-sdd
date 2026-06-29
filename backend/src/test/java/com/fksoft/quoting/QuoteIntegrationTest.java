package com.fksoft.quoting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.OverrideQuoteRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.PriceOrigin;
import com.fksoft.domain.quoting.QuoteStatus;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for quoting (SPEC-0005) against real Postgres, exercising the real
 * Accounts/Exchange/Commissioning/CommercialPolicy facades: the Orlando-car composition, override
 * with/without reason, rate-missing 422, account-not-found 404, and provenance immutability.
 */
class QuoteIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM accounts");
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
  }

  @Test
  void composesTheOrlandoCarSaleWithFrozenProvenance() {
    UUID accountId = createAccount();
    pinRate("USD/BRL", "5.40");

    ResponseEntity<QuoteView> response =
        restTemplate.postForEntity("/api/quotes", composeOrlando(accountId), QuoteView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    QuoteView quote = response.getBody();
    assertThat(quote).isNotNull();
    assertThat(quote.priceOrigin()).isEqualTo(PriceOrigin.MANUAL);
    assertThat(quote.fxRate()).isEqualByComparingTo("5.40");
    assertThat(quote.baseConverted()).isEqualTo(Money.of(new BigDecimal("2700.00"), "BRL"));
    assertThat(quote.commission().supplier()).isEqualTo(Money.of(new BigDecimal("405.00"), "BRL"));
    assertThat(quote.commission().agent()).isEqualTo(Money.of(new BigDecimal("270.00"), "BRL"));
    assertThat(quote.commission().spread()).isEqualTo(Money.of(new BigDecimal("135.00"), "BRL"));
    assertThat(quote.commission().spreadNegative()).isFalse();
    assertThat(quote.markup().source()).isEqualTo("SYSTEM_DEFAULT");
    assertThat(quote.suggestedAmount()).isEqualTo(Money.of(new BigDecimal("2700.00"), "BRL"));
    assertThat(quote.appliedAmount()).isEqualTo(Money.of(new BigDecimal("2700.00"), "BRL"));
    assertThat(quote.status()).isEqualTo(QuoteStatus.COMPOSED);
    assertThat(quote.provenance().rateId()).isNotNull();
    assertThat(quote.overrides()).isEmpty();
  }

  @Test
  void appliesOverrideWithReasonAndRejectsEmptyReason() {
    UUID accountId = createAccount();
    pinRate("USD/BRL", "5.40");
    QuoteView quote =
        restTemplate
            .postForEntity("/api/quotes", composeOrlando(accountId), QuoteView.class)
            .getBody();
    assertThat(quote).isNotNull();

    ResponseEntity<QuoteView> overridden =
        restTemplate.postForEntity(
            "/api/quotes/" + quote.id() + "/override",
            new OverrideQuoteRequest(
                Money.of(new BigDecimal("2650.00"), "BRL"), "fechamento com cliente recorrente"),
            QuoteView.class);
    assertThat(overridden.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(overridden.getBody()).isNotNull();
    assertThat(overridden.getBody().appliedAmount())
        .isEqualTo(Money.of(new BigDecimal("2650.00"), "BRL"));
    assertThat(overridden.getBody().overrides()).hasSize(1);
    assertThat(overridden.getBody().overrides().get(0).toAmount())
        .isEqualTo(Money.of(new BigDecimal("2650.00"), "BRL"));

    ResponseEntity<ApiErrorResponse> noReason =
        restTemplate.postForEntity(
            "/api/quotes/" + quote.id() + "/override",
            new OverrideQuoteRequest(Money.of(new BigDecimal("2600.00"), "BRL"), ""),
            ApiErrorResponse.class);
    assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(noReason.getBody()).isNotNull();
    assertThat(noReason.getBody().code()).isEqualTo("quoting.override.reason-required");
  }

  @Test
  void returns422WhenNoRateForPair() {
    UUID accountId = createAccount();

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/quotes", composeOrlando(accountId), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("quoting.rate.missing");
  }

  @Test
  void returns404WhenAccountDoesNotExist() {
    pinRate("USD/BRL", "5.40");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/quotes", composeOrlando(UUID.randomUUID()), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("quoting.account.not-found");
  }

  @Test
  void freezesProvenanceSoLaterRateChangesDoNotAlterTheQuote() {
    UUID accountId = createAccount();
    pinRate("USD/BRL", "5.40");
    QuoteView quote =
        restTemplate
            .postForEntity("/api/quotes", composeOrlando(accountId), QuoteView.class)
            .getBody();
    assertThat(quote).isNotNull();

    pinRate("USD/BRL", "5.55");

    ResponseEntity<QuoteView> reloaded =
        restTemplate.getForEntity("/api/quotes/" + quote.id(), QuoteView.class);
    assertThat(reloaded.getBody()).isNotNull();
    assertThat(reloaded.getBody().fxRate()).isEqualByComparingTo("5.40");
    assertThat(reloaded.getBody().suggestedAmount())
        .isEqualTo(Money.of(new BigDecimal("2700.00"), "BRL"));
  }

  private UUID createAccount() {
    AccountResponse account =
        restTemplate
            .postForEntity(
                "/api/accounts",
                new CreateAccountRequest(
                    LegalType.CNPJ, "12345678000195", "Agência Sol e Mar", null, null),
                AccountResponse.class)
            .getBody();
    assertThat(account).isNotNull();
    return account.id();
  }

  private void pinRate(String pair, String rate) {
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest(pair, new BigDecimal(rate), null, null),
        Void.class);
  }

  private static ComposeQuoteRequest composeOrlando(UUID accountId) {
    return new ComposeQuoteRequest(
        accountId,
        Money.of(new BigDecimal("500.00"), "USD"),
        "USD/BRL",
        new BigDecimal("0.15"),
        new BigDecimal("0.10"),
        null);
  }
}
