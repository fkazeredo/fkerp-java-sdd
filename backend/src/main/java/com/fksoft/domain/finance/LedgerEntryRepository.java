package com.fksoft.domain.finance;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link LedgerEntry}. Module-internal: only the Finance module uses it. */
@ModuleInternal
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

  /** All entries of a period, any status (used by the close-check via the LedgerDirectory). */
  List<LedgerEntry> findByPeriod(String period);

  /**
   * Paginated search with optional direction, status, period and party filters (each applied only
   * when non-null). An empty result yields an empty page (never 404).
   */
  @Query(
      "select e from LedgerEntry e where (:direction is null or e.direction = :direction) "
          + "and (:status is null or e.status = :status) "
          + "and (:period is null or e.period = :period) "
          + "and (:party is null or e.partyId = :party)")
  Page<LedgerEntry> search(
      @Param("direction") LedgerDirection direction,
      @Param("status") EntryStatus status,
      @Param("period") String period,
      @Param("party") String party,
      Pageable pageable);
}
