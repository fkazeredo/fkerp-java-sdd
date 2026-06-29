package com.fksoft.domain.finance.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link AccountingPeriod}. Module-internal. {@code closePeriod} is a financial
 * transition, so it loads the period with a pessimistic write lock (persistence.md; SPEC-0015
 * "locking pessimista no período").
 */
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, String> {

  /** Loads a period for update with a pessimistic write lock (close transition). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from AccountingPeriod p where p.period = :period")
  Optional<AccountingPeriod> findByIdForUpdate(@Param("period") String period);
}
