package com.fksoft.quoting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.IssueDirectiveRequest;
import com.fksoft.application.api.dto.ParameterRuleResponse;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.commercialpolicy.ParameterValueTypeCodes;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boundary regression proving the SPEC-0005 markup stub is truly GRADUATED by SPEC-0014 (DL-0040):
 * the markup that flows into a freshly composed Quote now comes from the governed precedence
 * engine.
 *
 * <ul>
 *   <li>With a director's MARKUP_PCT directive for the account, the Quote applies a non-default
 *       markup and its provenance reports {@code DIRECTIVE} (not {@code SYSTEM_DEFAULT}).
 *   <li>With no rule above the default, the Quote still composes with markup 0 / {@code
 *       SYSTEM_DEFAULT} — back-compatible with the pre-graduation behaviour.
 * </ul>
 */
class MarkupGraduationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM accounts");
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
    jdbcTemplate.execute("DELETE FROM parameter_rules WHERE defined_by <> 'system-seed'");
  }

  @Test
  void aDirectiveMarkupFlowsIntoAFreshlyComposedQuote() {
    UUID accountId = createAccount();
    pinRate("USD/BRL", "5.40");

    // Director sets a 10% markup directive for this account.
    ResponseEntity<ParameterRuleResponse> directive =
        restTemplate.postForEntity(
            "/api/commercial-policy/directives",
            new IssueDirectiveRequest(
                "MARKUP_PCT",
                "0.10",
                ParameterValueTypeCodes.PERCENT,
                accountId,
                null,
                null,
                null,
                null,
                "promoção dirigida para a conta"),
            ParameterRuleResponse.class);
    assertThat(directive.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    QuoteView quote =
        restTemplate
            .postForEntity("/api/quotes", composeOrlando(accountId), QuoteView.class)
            .getBody();

    assertThat(quote).isNotNull();
    // base converted = 500 * 5.40 = 2700.00 ; markup 10% => 270.00 ; suggested = 2970.00
    assertThat(quote.markup().source()).isEqualTo("DIRECTIVE");
    assertThat(quote.markup().pct()).isEqualByComparingTo("0.10");
    assertThat(quote.markup().amount()).isEqualTo(Money.of(new BigDecimal("270.00"), "BRL"));
    assertThat(quote.suggestedAmount()).isEqualTo(Money.of(new BigDecimal("2970.00"), "BRL"));
  }

  @Test
  void withoutAnyRuleTheQuoteStillUsesTheSystemDefaultMarkup() {
    UUID accountId = createAccount();
    pinRate("USD/BRL", "5.40");

    QuoteView quote =
        restTemplate
            .postForEntity("/api/quotes", composeOrlando(accountId), QuoteView.class)
            .getBody();

    assertThat(quote).isNotNull();
    assertThat(quote.markup().source()).isEqualTo("SYSTEM_DEFAULT");
    assertThat(quote.markup().pct()).isEqualByComparingTo("0");
    assertThat(quote.suggestedAmount()).isEqualTo(Money.of(new BigDecimal("2700.00"), "BRL"));
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
