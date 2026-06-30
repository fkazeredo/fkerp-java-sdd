package com.fksoft.domain.intelligence;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the per-agency {@link AgencyFxAccrual} totals. Module-internal. */
@ModuleInternal
public interface AgencyFxAccrualRepository extends JpaRepository<AgencyFxAccrual, UUID> {}
