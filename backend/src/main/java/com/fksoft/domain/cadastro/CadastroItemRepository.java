package com.fksoft.domain.cadastro;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/query repository for the {@link CadastroItem} aggregate (SPEC-0031). Module-internal:
 * other modules never touch it — they validate codes through the {@link CadastroValidator} public
 * port (Spring Modulith).
 */
@ModuleInternal
public interface CadastroItemRepository extends JpaRepository<CadastroItem, UUID> {

  /** All items of a type, ordered active-first then by sort order then code. */
  List<CadastroItem> findByTypeOrderByActiveDescSortOrderAscCodeAsc(CadastroType type);

  /** The item of a type with the given code, if any. */
  Optional<CadastroItem> findByTypeAndCode(CadastroType type, String code);

  /** Whether an active item exists for the given type and code (the validation query, BR3). */
  boolean existsByTypeAndCodeAndActiveTrue(CadastroType type, String code);
}
