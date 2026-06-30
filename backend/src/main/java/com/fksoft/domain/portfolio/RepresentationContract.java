package com.fksoft.domain.portfolio;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Representation contract aggregate root (SPEC-0020 BR2): the agreement that gives the Acme the
 * right to sell a brand, with a validity window and a pointer to the contract document in the
 * Compliance vault ({@code documentId}, value — never an FK). The reference commercial {@code
 * terms} are a validated free map (conditions, not prices — BR6), stored as jsonb. Module-internal.
 *
 * <p>The {@code expiringSignaledAt} flag makes the expiry alert <strong>idempotent</strong>
 * (DL-0063): the controlled-clock job marks a contract once when it falls inside the warning
 * window, so re-running the job never re-publishes {@code RepresentationExpiring}.
 */
@Entity
@Table(name = "representation_contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class RepresentationContract {

  /** Days before {@code validUntil} a contract is considered "expiring" (DL-0063, default 30). */
  static final int EXPIRY_WARNING_DAYS = 30;

  @Id private UUID id;

  private String brandRef;
  private LocalDate validFrom;
  private LocalDate validUntil;
  private UUID documentId;

  @JdbcTypeCode(SqlTypes.JSON)
  private String termsJson;

  private Instant expiringSignaledAt;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new representation contract (BR2).
   *
   * @param brandRef the covered brand (value, required)
   * @param validFrom the start of validity (required)
   * @param validUntil the end of validity, or {@code null} when open-ended
   * @param documentId the Compliance document id (value), or {@code null}
   * @param terms the reference commercial terms (free map), or {@code null}
   * @param now the creation instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable contract
   * @throws RepresentationContractInvalidException when validFrom is missing or validUntil precedes
   *     validFrom (BR2)
   */
  public static RepresentationContract register(
      String brandRef,
      LocalDate validFrom,
      LocalDate validUntil,
      UUID documentId,
      Map<String, String> terms,
      Instant now,
      String actor) {
    if (brandRef == null || brandRef.isBlank() || validFrom == null) {
      throw new RepresentationContractInvalidException();
    }
    if (validUntil != null && validUntil.isBefore(validFrom)) {
      throw new RepresentationContractInvalidException();
    }
    RepresentationContract contract = new RepresentationContract();
    contract.id = UUID.randomUUID();
    contract.brandRef = brandRef.trim();
    contract.validFrom = validFrom;
    contract.validUntil = validUntil;
    contract.documentId = documentId;
    contract.termsJson = TermsCodec.encode(terms);
    contract.createdAt = now;
    contract.updatedAt = now;
    contract.createdBy = actor;
    contract.updatedBy = actor;
    return contract;
  }

  /**
   * Whether this contract is in force on the given date (SPEC-0020 BR2; DL-0061): {@code validFrom
   * <= on} and ({@code validUntil} is open or {@code on <= validUntil}).
   *
   * @param on the date to check
   * @return {@code true} when the contract covers that date
   */
  public boolean isInForceOn(LocalDate on) {
    boolean started = !on.isBefore(validFrom);
    boolean notEnded = validUntil == null || !on.isAfter(validUntil);
    return started && notEnded;
  }

  /**
   * Signals the expiry of this contract once if it is due within the warning window (DL-0063):
   * {@code validUntil} is set and on/before {@code asOf + EXPIRY_WARNING_DAYS}, and it has not been
   * signaled yet. Idempotent — a second call returns {@code false}.
   *
   * @param now the evaluation instant (UTC, controlled clock)
   * @return {@code true} when this call newly signaled the expiry
   */
  public boolean signalExpiringIfDue(Instant now, LocalDate asOf) {
    if (validUntil == null || expiringSignaledAt != null) {
      return false;
    }
    LocalDate warningThreshold = asOf.plusDays(EXPIRY_WARNING_DAYS);
    if (validUntil.isAfter(warningThreshold)) {
      return false; // not yet within the warning window
    }
    this.expiringSignaledAt = now;
    this.updatedAt = now;
    return true;
  }

  /** The contract id. */
  public UUID id() {
    return id;
  }

  /** The covered brand (value). */
  public String brandRef() {
    return brandRef;
  }

  /** The end of validity, or {@code null} when open-ended. */
  public LocalDate validUntil() {
    return validUntil;
  }

  /** Projects the aggregate to its public read view. */
  public ContractView toView() {
    return new ContractView(
        id, brandRef, validFrom, validUntil, documentId, TermsCodec.decode(termsJson), createdAt);
  }
}
