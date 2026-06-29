package com.fksoft.infra.integration.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the {@link MockPayoutJob} async queue. Infra-only. */
interface MockPayoutJobRepository extends JpaRepository<MockPayoutJob, UUID> {

  /** Undelivered jobs whose delivery time has arrived (the dispatcher's work list). */
  List<MockPayoutJob> findByDeliveredFalseAndDeliverAfterLessThanEqual(Instant now);
}
