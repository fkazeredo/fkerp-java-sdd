package com.fksoft.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.cadastro.CadastroItem;
import com.fksoft.domain.cadastro.CadastroItemInvalidException;
import com.fksoft.domain.cadastro.CadastroType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link CadastroItem} aggregate (SPEC-0031 BR1/BR2): it is born active, the
 * code is normalized and immutable, and only label/active/sortOrder change on update.
 */
class CadastroItemTest {

  private static final Instant NOW = Instant.parse("2026-07-01T10:00:00Z");

  @Test
  void createNormalizesCodeToUpperCaseAndIsBornActive() {
    CadastroItem item =
        CadastroItem.create(CadastroType.ASSET_TYPE, " equipment ", "Equipamento", 10, NOW, "op");

    assertThat(item.code()).isEqualTo("EQUIPMENT");
    assertThat(item.type()).isEqualTo(CadastroType.ASSET_TYPE);
    assertThat(item.isActive()).isTrue();
    assertThat(item.toView().label()).isEqualTo("Equipamento");
    assertThat(item.toView().sortOrder()).isEqualTo(10);
  }

  @Test
  void createRejectsMissingMandatoryData() {
    assertThatThrownBy(
            () -> CadastroItem.create(CadastroType.ASSET_TYPE, "  ", "Rótulo", 0, NOW, "op"))
        .isInstanceOf(CadastroItemInvalidException.class);
    assertThatThrownBy(
            () -> CadastroItem.create(CadastroType.ASSET_TYPE, "CODE", "  ", 0, NOW, "op"))
        .isInstanceOf(CadastroItemInvalidException.class);
    assertThatThrownBy(() -> CadastroItem.create(null, "CODE", "Rótulo", 0, NOW, "op"))
        .isInstanceOf(CadastroItemInvalidException.class);
  }

  @Test
  void updateChangesLabelActiveAndOrderButNotCode() {
    CadastroItem item =
        CadastroItem.create(CadastroType.TAX_REGIME, "SIMPLES_NACIONAL", "Simples", 10, NOW, "op");

    item.update("Simples Nacional", false, 99, NOW, "editor");

    assertThat(item.code()).isEqualTo("SIMPLES_NACIONAL"); // immutable
    assertThat(item.toView().label()).isEqualTo("Simples Nacional");
    assertThat(item.isActive()).isFalse();
    assertThat(item.toView().sortOrder()).isEqualTo(99);
  }

  @Test
  void deactivateIsIdempotentSoftDelete() {
    CadastroItem item =
        CadastroItem.create(CadastroType.TAX_REGIME, "LUCRO_REAL", "Lucro Real", 30, NOW, "op");

    item.deactivate(NOW, "editor");
    item.deactivate(NOW, "editor");

    assertThat(item.isActive()).isFalse();
    assertThat(item.code()).isEqualTo("LUCRO_REAL");
  }

  @Test
  void updateRejectsBlankLabel() {
    CadastroItem item =
        CadastroItem.create(CadastroType.ASSET_TYPE, "OTHER", "Outros", 30, NOW, "op");
    assertThatThrownBy(() -> item.update("  ", true, 0, NOW, "editor"))
        .isInstanceOf(CadastroItemInvalidException.class);
  }
}
