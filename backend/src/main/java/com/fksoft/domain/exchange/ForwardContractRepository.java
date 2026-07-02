package com.fksoft.domain.exchange;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Repository of FX forward contracts (SPEC-0032, DL-0130). Module-internal. */
@ModuleInternal
public interface ForwardContractRepository extends Repository<ForwardContract, UUID> {

  ForwardContract save(ForwardContract forward);

  Optional<ForwardContract> findById(UUID id);

  List<ForwardContract> findByStatusOrderByMaturityDateAsc(ForwardStatus status);

  List<ForwardContract> findAllByOrderByCreatedAtDesc();

  List<ForwardContract> findByStatusAndCurrency(ForwardStatus status, String currency);
}
