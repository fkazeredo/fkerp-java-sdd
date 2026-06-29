package com.fksoft.domain.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.accounts.AccountStatus;
import com.fksoft.domain.accounts.Document;
import com.fksoft.domain.accounts.LegalType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Account} aggregate invariants (SPEC-0002 BR4): a new account is born
 * ACTIVE.
 */
class AccountTest {

  @Test
  void registerStartsActiveWithGeneratedIdAndAudit() {
    Instant now = Instant.parse("2026-06-26T12:00:00Z");
    Document document = Document.of(LegalType.CNPJ, "12345678000195");

    Account account =
        Account.register(
            document, "Agência Sol e Mar", "26.123456.10.0001-9", null, now, "operador1");

    assertThat(account.id()).isNotNull();
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.legalType()).isEqualTo(LegalType.CNPJ);
    assertThat(account.documentNumber()).isEqualTo("12345678000195");
    assertThat(account.displayName()).isEqualTo("Agência Sol e Mar");
    assertThat(account.cadastur()).isEqualTo("26.123456.10.0001-9");
    assertThat(account.iata()).isNull();
    assertThat(account.createdAt()).isEqualTo(now);
    assertThat(account.createdBy()).isEqualTo("operador1");
  }
}
