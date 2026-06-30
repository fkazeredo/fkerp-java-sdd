package com.fksoft.domain.admin.internal;

import com.fksoft.domain.admin.AdminSupplierStatus;
import com.fksoft.domain.admin.AdminSupplierType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/query repository for the {@link AdminSupplier} aggregate (SPEC-0025). Module-internal:
 * other modules never touch it (Spring Modulith).
 */
public interface AdminSupplierRepository extends JpaRepository<AdminSupplier, UUID> {

  /** All suppliers, newest first. */
  List<AdminSupplier> findAllByOrderByCreatedAtDesc();

  /** All suppliers of a type, newest first. */
  List<AdminSupplier> findByTypeOrderByCreatedAtDesc(AdminSupplierType type);

  /** All suppliers in a status, newest first. */
  List<AdminSupplier> findByStatusOrderByCreatedAtDesc(AdminSupplierStatus status);

  /** All suppliers of a type and status, newest first. */
  List<AdminSupplier> findByTypeAndStatusOrderByCreatedAtDesc(
      AdminSupplierType type, AdminSupplierStatus status);
}
