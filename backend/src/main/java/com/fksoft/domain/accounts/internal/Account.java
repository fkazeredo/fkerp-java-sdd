package com.fksoft.domain.accounts.internal;

import com.fksoft.domain.accounts.AccountStatus;
import com.fksoft.domain.accounts.Document;
import com.fksoft.domain.accounts.LegalType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Commercial account aggregate (agency or agent). Holds the partner's commercial and legal identity
 * only — it never computes money (BR6). The document's structural validity is guaranteed by the
 * {@link Document} value object before the entity is built; a new account is always born {@link
 * AccountStatus#ACTIVE} (BR4). Module-internal: other modules collaborate through the {@code
 * accounts} module facade, never with this entity.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private LegalType legalType;

  private String documentNumber;

  private String displayName;

  @Enumerated(EnumType.STRING)
  private AccountStatus status;

  private String cadastur;

  private String iata;

  private Instant createdAt;

  private Instant updatedAt;

  private String createdBy;

  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new account from an already-valid {@link Document}. The account starts {@link
   * AccountStatus#ACTIVE}; optional registrations ({@code cadastur}, {@code iata}) are stored as
   * provided and never validated against external registries (BR5).
   *
   * @param document the validated legal document (legal type + digits)
   * @param displayName non-empty commercial display name (BR4)
   * @param cadastur optional CADASTUR code, or {@code null}
   * @param iata optional IATA code, or {@code null}
   * @param now creation instant (UTC)
   * @param actor the user performing the registration (audit)
   * @return a new, persistable account
   */
  public static Account register(
      Document document,
      String displayName,
      String cadastur,
      String iata,
      Instant now,
      String actor) {
    Account account = new Account();
    account.id = UUID.randomUUID();
    account.legalType = document.legalType();
    account.documentNumber = document.number();
    account.displayName = displayName;
    account.status = AccountStatus.ACTIVE;
    account.cadastur = cadastur;
    account.iata = iata;
    account.createdAt = now;
    account.updatedAt = now;
    account.createdBy = actor;
    account.updatedBy = actor;
    return account;
  }
}
