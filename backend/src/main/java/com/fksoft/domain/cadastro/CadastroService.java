package com.fksoft.domain.cadastro;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Cadastro module (SPEC-0031; ADR-0019): the generic registry of
 * editable reference data. It lists the convertible {@link CadastroType}s, lists/creates/updates/
 * deactivates {@link CadastroItem}s (codes immutable once created; label/active/sortOrder
 * editable), and implements the public {@link CadastroValidator} port other modules use to validate
 * a code.
 *
 * <p>This module is a <strong>leaf</strong>: it depends only on its own repository and the {@code
 * error} kernel — no other business module. The dependency direction is {@code caller → cadastro},
 * so the module graph stays acyclic (Spring Modulith).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CadastroService implements CadastroValidator {

  private final CadastroItemRepository items;
  private final Clock clock;

  /** The catalogue of convertible reference-data types (SPEC-0031). */
  @Transactional(readOnly = true)
  public List<CadastroType> listTypes() {
    return List.of(CadastroType.values());
  }

  /**
   * Lists the items of a type, active-first then by sort order (SPEC-0031 AC1).
   *
   * @param type the cadastro type
   * @return the items of the type
   */
  @Transactional(readOnly = true)
  public List<CadastroItemView> listItems(CadastroType type) {
    return items.findByTypeOrderByActiveDescSortOrderAscCodeAsc(type).stream()
        .map(CadastroItem::toView)
        .toList();
  }

  /**
   * Creates a new cadastro item (BR1). Rejects a duplicate {@code (type, code)}.
   *
   * @param command the item details
   * @param actor who creates it (audit)
   * @return the persisted item view
   * @throws CadastroItemInvalidException when a mandatory field is missing (BR1)
   * @throws CadastroItemDuplicateException when the (type, code) already exists (BR1)
   */
  @Transactional
  public CadastroItemView create(CreateCadastroItemCommand command, String actor) {
    if (command == null) {
      throw new CadastroItemInvalidException();
    }
    Instant now = clock.instant();
    CadastroItem item =
        CadastroItem.create(
            command.type(), command.code(), command.label(), command.sortOrder(), now, actor);
    try {
      items.saveAndFlush(item);
    } catch (DataIntegrityViolationException duplicate) {
      throw new CadastroItemDuplicateException();
    }
    log.info(
        "CadastroItemCreated id={} type={} code={} by={}",
        item.id(),
        item.type(),
        item.code(),
        actor);
    return item.toView();
  }

  /**
   * Updates the editable fields of an item (BR2): label, active and sortOrder. The code is
   * immutable.
   *
   * @param id the item id
   * @param command the new editable values
   * @param actor who updates it (audit)
   * @return the updated item view
   * @throws CadastroItemNotFoundException when no item has that id
   * @throws CadastroItemInvalidException when the label is missing (BR2)
   */
  @Transactional
  public CadastroItemView update(UUID id, UpdateCadastroItemCommand command, String actor) {
    if (command == null) {
      throw new CadastroItemInvalidException();
    }
    CadastroItem item = items.findById(id).orElseThrow(CadastroItemNotFoundException::new);
    item.update(command.label(), command.active(), command.sortOrder(), clock.instant(), actor);
    items.save(item);
    log.info(
        "CadastroItemUpdated id={} type={} code={} active={} by={}",
        id,
        item.type(),
        item.code(),
        command.active(),
        actor);
    return item.toView();
  }

  /**
   * Deactivates an item (soft delete, BR2). Idempotent.
   *
   * @param id the item id
   * @param actor who deactivates it (audit)
   * @return the updated item view
   * @throws CadastroItemNotFoundException when no item has that id
   */
  @Transactional
  public CadastroItemView deactivate(UUID id, String actor) {
    CadastroItem item = items.findById(id).orElseThrow(CadastroItemNotFoundException::new);
    item.deactivate(clock.instant(), actor);
    items.save(item);
    log.info(
        "CadastroItemDeactivated id={} type={} code={} by={}", id, item.type(), item.code(), actor);
    return item.toView();
  }

  // --- CadastroValidator port (BR3) ---

  @Override
  @Transactional(readOnly = true)
  public void validate(CadastroType type, String code) {
    if (!isValid(type, code)) {
      throw new CadastroCodeInvalidException(type, code);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isValid(CadastroType type, String code) {
    if (type == null || code == null || code.isBlank()) {
      return false;
    }
    return items.existsByTypeAndCodeAndActiveTrue(type, code.trim().toUpperCase(Locale.ROOT));
  }
}
