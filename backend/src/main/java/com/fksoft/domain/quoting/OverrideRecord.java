package com.fksoft.domain.quoting;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One step in a quote's override history (BR6): the applied amount moved from {@code fromAmount} to
 * {@code toAmount} for a non-empty {@code reason}, by someone, at some instant. Amounts are in the
 * quote's sale currency. Module-internal.
 */
@Entity
@Table(name = "override_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class OverrideRecord {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quote_id", nullable = false)
  private Quote quote;

  private BigDecimal fromAmount;

  private BigDecimal toAmount;

  private String reason;

  private String performedBy;

  private Instant performedAt;

  static OverrideRecord of(
      Quote quote,
      BigDecimal fromAmount,
      BigDecimal toAmount,
      String reason,
      String performedBy,
      Instant performedAt) {
    OverrideRecord record = new OverrideRecord();
    record.id = UUID.randomUUID();
    record.quote = quote;
    record.fromAmount = fromAmount;
    record.toAmount = toAmount;
    record.reason = reason;
    record.performedBy = performedBy;
    record.performedAt = performedAt;
    return record;
  }
}
