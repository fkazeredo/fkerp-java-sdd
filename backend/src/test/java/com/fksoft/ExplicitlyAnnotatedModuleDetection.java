package com.fksoft;

import java.util.stream.Stream;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

/**
 * Spring Modulith module-detection strategy used by the architecture test suite: only packages
 * explicitly annotated with {@code @ApplicationModule} are treated as modules (DL-0006).
 *
 * <p>Lives in test scope and is registered via {@code META-INF/spring.factories} so it is picked up
 * deterministically by the static {@code ApplicationModules.of(...)} call in {@code
 * ModularityTests} — independent of any Spring context. This keeps the {@code domain}/{@code
 * application}/{@code infra} layers from being misread as modules — which would wrongly flag the
 * allowed {@code application -> infra} dependency (ADR 0012). At runtime the same strategy is
 * selected via {@code spring.modulith.detection-strategy} in {@code application.yml}.
 */
public class ExplicitlyAnnotatedModuleDetection implements ApplicationModuleDetectionStrategy {

  private final ApplicationModuleDetectionStrategy delegate =
      ApplicationModuleDetectionStrategy.explicitlyAnnotated();

  @Override
  public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
    return delegate.getModuleBasePackages(basePackage);
  }
}
