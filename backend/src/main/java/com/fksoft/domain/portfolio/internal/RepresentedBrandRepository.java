package com.fksoft.domain.portfolio.internal;

import com.fksoft.domain.portfolio.BrandStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/query repository for the {@link RepresentedBrand} aggregate (SPEC-0020). Module-internal:
 * other modules never touch it (Spring Modulith).
 */
public interface RepresentedBrandRepository extends JpaRepository<RepresentedBrand, UUID> {

  /** A brand by its unique business identifier (value). */
  Optional<RepresentedBrand> findByBrandRef(String brandRef);

  /** Whether a brand with the given brandRef already exists (duplicate guard, BR1). */
  boolean existsByBrandRef(String brandRef);

  /** All brands with the given status, newest first. */
  List<RepresentedBrand> findByStatusOrderByCreatedAtDesc(BrandStatus status);

  /** All brands, newest first. */
  List<RepresentedBrand> findAllByOrderByCreatedAtDesc();
}
