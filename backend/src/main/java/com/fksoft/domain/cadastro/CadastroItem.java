package com.fksoft.domain.cadastro;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Column;
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
 * A single editable reference-data item (SPEC-0031 BR1; ADR-0019): one {@code code} of a {@link
 * CadastroType}, with a human {@code label} (pt-BR), an {@code active} flag and a {@code sortOrder}.
 * Replaces a former business enum constant. The {@code code} equals the old enum constant name so
 * the wire contract is unchanged (DL-0115).
 *
 * <p>The {@code code} is <strong>immutable</strong> after creation (BR2): a persisted value must
 * keep resolving. Only {@code label}, {@code active} and {@code sortOrder} change. Deactivation is a
 * soft operation — the row is never deleted, preserving round-trip of already-persisted codes.
 * Module-internal.
 */
@Entity
@Table(name = "cadastro_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class CadastroItem {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private CadastroType type;

  private String code;
  private String label;

  @Column(name = "active")
  private boolean active;

  private int sortOrder;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Creates a new cadastro item, born {@code active} (BR1). The {@code code} is normalized to upper
   * case (reference codes are upper-case constants, like the old enum names).
   *
   * @param type the cadastro type (required)
   * @param code the immutable code (required) — equals the old enum constant name
   * @param label the human label in pt-BR (required)
   * @param sortOrder the display order (lower first)
   * @param now the creation instant (UTC)
   * @param actor who creates it (audit)
   * @return a new, persistable active item
   * @throws CadastroItemInvalidException when a mandatory field is missing/blank
   */
  public static CadastroItem create(
      CadastroType type, String code, String label, int sortOrder, Instant now, String actor) {
    if (type == null || code == null || code.isBlank() || label == null || label.isBlank()) {
      throw new CadastroItemInvalidException();
    }
    CadastroItem item = new CadastroItem();
    item.id = UUID.randomUUID();
    item.type = type;
    item.code = code.trim().toUpperCase(java.util.Locale.ROOT);
    item.label = label.trim();
    item.active = true;
    item.sortOrder = sortOrder;
    item.createdAt = now;
    item.updatedAt = now;
    item.createdBy = actor;
    item.updatedBy = actor;
    return item;
  }

  /**
   * Updates the editable fields (BR2): {@code label}, {@code active} and {@code sortOrder}. The
   * {@code code} and {@code type} never change.
   *
   * @param label the new label (required)
   * @param active the new active flag
   * @param sortOrder the new display order
   * @param now the update instant (UTC)
   * @param actor who updates it (audit)
   * @throws CadastroItemInvalidException when the label is missing/blank
   */
  public void update(String label, boolean active, int sortOrder, Instant now, String actor) {
    if (label == null || label.isBlank()) {
      throw new CadastroItemInvalidException();
    }
    this.label = label.trim();
    this.active = active;
    this.sortOrder = sortOrder;
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /**
   * Deactivates the item (soft delete, BR2): sets {@code active=false}. Idempotent.
   *
   * @param now the update instant (UTC)
   * @param actor who deactivates it (audit)
   */
  public void deactivate(Instant now, String actor) {
    this.active = false;
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /** The item id. */
  public UUID id() {
    return id;
  }

  /** The cadastro type. */
  public CadastroType type() {
    return type;
  }

  /** The immutable code (= old enum constant name). */
  public String code() {
    return code;
  }

  /** Whether the item is active (usable as a validated code). */
  public boolean isActive() {
    return active;
  }

  /** Projects the aggregate to its public read view. */
  public CadastroItemView toView() {
    return new CadastroItemView(id, type, code, label, active, sortOrder, createdAt);
  }
}
