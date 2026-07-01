package com.fksoft.domain.booking;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Administrable source of the cancellation/no-show policy for a product/supplier scope (SPEC-0010):
 * the authoritative record from which a booking freezes its snapshot at confirmation (BR1). One row
 * per {@code scopeRef} (the product/supplier reference, a value — no cross-module FK).
 * Module-internal.
 */
@Entity
@Table(name = "cancellation_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class CancellationPolicySource {

  @Id private UUID id;

  private String scopeRef;

  /** The cancellation-type cadastro code (was {@code CancellationType}; SPEC-0031/DL-0117). */
  private String type;

  private String windowsEncoded;

  private boolean refundable;

  @Enumerated(EnumType.STRING)
  private CostBearer costBearer;

  private boolean merchantOfRecord;

  private BigDecimal noShowFeeAmount;
  private String noShowFeeCurrency;
  private boolean waivedIfFlightCancelled;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Creates a new policy source for a scope.
   *
   * @param scopeRef the product/supplier scope reference (non-empty)
   * @param policy the cancellation policy value object
   * @param noShow the no-show policy
   * @param now creation instant (UTC)
   * @param actor who created it (audit)
   * @return a new, persistable policy source
   */
  public static CancellationPolicySource create(
      String scopeRef, CancellationPolicy policy, NoShowPolicy noShow, Instant now, String actor) {
    CancellationPolicySource source = new CancellationPolicySource();
    source.id = UUID.randomUUID();
    source.scopeRef = scopeRef;
    source.createdAt = now;
    source.createdBy = actor;
    source.apply(policy, noShow, now, actor);
    return source;
  }

  /**
   * Replaces the policy/no-show content (used by the admin PUT upsert), updating the audit fields.
   */
  public void apply(CancellationPolicy policy, NoShowPolicy noShow, Instant now, String actor) {
    this.type = policy.type();
    this.windowsEncoded = PenaltyWindowsCodec.encode(policy.windows());
    this.refundable = policy.refundable();
    this.costBearer = policy.costBearer();
    this.merchantOfRecord = policy.merchantOfRecord();
    this.noShowFeeAmount = noShow.fee() == null ? null : noShow.fee().amount();
    this.noShowFeeCurrency = noShow.fee() == null ? null : noShow.fee().currency();
    this.waivedIfFlightCancelled = noShow.waivedIfFlightCancelled();
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /** The cancellation policy value object reconstructed from this source. */
  public CancellationPolicy toPolicy() {
    return new CancellationPolicy(
        type, PenaltyWindowsCodec.decode(windowsEncoded), refundable, costBearer, merchantOfRecord);
  }

  /** The no-show policy value object reconstructed from this source. */
  public NoShowPolicy toNoShowPolicy() {
    Money fee = noShowFeeAmount == null ? null : Money.of(noShowFeeAmount, noShowFeeCurrency);
    return new NoShowPolicy(fee, waivedIfFlightCancelled);
  }

  /** Projects this aggregate to its public read view. */
  public CancellationPolicyView toView() {
    return new CancellationPolicyView(
        scopeRef,
        type,
        PenaltyWindowsCodec.decode(windowsEncoded),
        refundable,
        costBearer,
        merchantOfRecord,
        noShowFeeAmount == null ? null : Money.of(noShowFeeAmount, noShowFeeCurrency),
        waivedIfFlightCancelled);
  }
}
