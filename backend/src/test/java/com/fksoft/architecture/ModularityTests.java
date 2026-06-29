package com.fksoft.architecture;

import com.fksoft.FkErpApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith boundary gate (SPEC-0001). With {@code explicitly-annotated} detection (DL-0006)
 * the foundation has no business modules yet, so {@code verify()} is a no-op pass; it becomes
 * meaningful once {@code @ApplicationModule}-annotated modules appear in Phase 1, enforcing
 * inter-module boundaries.
 */
class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(FkErpApplication.class);

  @Test
  void verifiesModularStructure() {
    modules.verify();
  }
}
