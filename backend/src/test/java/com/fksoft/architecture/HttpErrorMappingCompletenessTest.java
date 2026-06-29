package com.fksoft.architecture;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.error.DomainException;
import com.fksoft.infra.web.HttpErrorMapping;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Build-time completeness check for the error registry (ADR 0011): every concrete {@link
 * DomainException} subtype must have an {@link HttpErrorMapping} entry, so the registry's default
 * (422) can never silently hide a forgotten mapping. The foundation has no business exceptions yet,
 * so the set is empty and the check passes — but it guards every future slice.
 */
class HttpErrorMappingCompletenessTest {

  @Test
  void everyConcreteDomainExceptionHasAnHttpStatusMapping() {
    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fksoft");

    Set<String> mapped =
        new HttpErrorMapping().mappedTypes().stream().map(Class::getName).collect(toSet());

    List<String> unmapped =
        production.stream()
            .filter(c -> c.isAssignableTo(DomainException.class))
            .filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
            .map(JavaClass::getFullName)
            .filter(name -> !mapped.contains(name))
            .toList();

    assertThat(unmapped)
        .as("every concrete DomainException must have an HttpErrorMapping entry (ADR 0011)")
        .isEmpty();
  }
}
