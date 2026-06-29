package com.fksoft.domain.intelligence.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the per-agency {@link AgencyFxAccrual} totals. Module-internal. */
public interface AgencyFxAccrualRepository extends JpaRepository<AgencyFxAccrual, UUID> {}
