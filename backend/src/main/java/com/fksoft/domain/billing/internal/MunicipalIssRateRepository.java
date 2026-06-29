package com.fksoft.domain.billing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Read repository for the seeded municipal ISS rates (SPEC-0016; DL-0044). Module-internal. */
interface MunicipalIssRateRepository extends JpaRepository<MunicipalIssRate, String> {}
