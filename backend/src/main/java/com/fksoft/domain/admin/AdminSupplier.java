package com.fksoft.domain.admin;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal administrative-supplier aggregate root (SPEC-0025 BR1): an administrative vendor — a
 * utility, software vendor, service provider or other — that the Acme pays recurring administrative
 * costs to. It is <strong>not</strong> a tourism brand/supplier (that is the Portfolio, SPEC-0020).
 * A supplier is born {@link AdminSupplierStatus#ACTIVE}. Module-internal.
 */
@Entity
@Table(name = "admin_suppliers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class AdminSupplier {

  @Id private UUID id;

  /** The supplier-type cadastro code (was {@code AdminSupplierType}; SPEC-0031/DL-0115). */
  private String type;

  private String identifier;
  private String displayName;

  @Enumerated(EnumType.STRING)
  private AdminSupplierStatus status;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new administrative supplier in {@link AdminSupplierStatus#ACTIVE} (BR1). Validates
   * the mandatory data (type and display name).
   *
   * @param type the supplier-type cadastro code (required; validated by the service)
   * @param identifier the legal identifier (CNPJ/CPF), or {@code null}
   * @param displayName the display name (required)
   * @param now the creation instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable ACTIVE supplier
   * @throws AdminSupplierInvalidException when a mandatory field is missing (BR1)
   */
  public static AdminSupplier register(
      String type, String identifier, String displayName, Instant now, String actor) {
    if (type == null || type.isBlank() || displayName == null || displayName.isBlank()) {
      throw new AdminSupplierInvalidException();
    }
    AdminSupplier supplier = new AdminSupplier();
    supplier.id = UUID.randomUUID();
    supplier.type = type;
    supplier.identifier = identifier == null || identifier.isBlank() ? null : identifier.trim();
    supplier.displayName = displayName.trim();
    supplier.status = AdminSupplierStatus.ACTIVE;
    supplier.createdAt = now;
    supplier.updatedAt = now;
    supplier.createdBy = actor;
    supplier.updatedBy = actor;
    return supplier;
  }

  /** The supplier id. */
  public UUID id() {
    return id;
  }

  /** The current status. */
  public AdminSupplierStatus status() {
    return status;
  }

  /** Projects the aggregate to its public read view. */
  public AdminSupplierView toView() {
    return new AdminSupplierView(id, type, identifier, displayName, status, createdAt);
  }
}
