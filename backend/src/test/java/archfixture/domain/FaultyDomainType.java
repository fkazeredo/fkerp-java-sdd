package archfixture.domain;

import archfixture.infra.SomeInfraType;

/**
 * Test fixture that deliberately violates the layer rule by having a {@code ..domain..} type depend
 * on an {@code ..infra..} type. {@code ArchitectureRulesHaveTeethTest} asserts the production rule
 * fails against this, proving the boundary gate has teeth.
 */
public class FaultyDomainType {

  private final SomeInfraType infra = new SomeInfraType();

  public String describe() {
    return infra.value();
  }
}
