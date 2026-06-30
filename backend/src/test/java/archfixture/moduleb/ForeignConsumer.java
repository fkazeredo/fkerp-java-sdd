package archfixture.moduleb;

import archfixture.modulea.SecretInternal;

/**
 * Test fixture that deliberately violates the module-internal encapsulation rule (Phase 9 / ADR
 * 0016) by having a class of fixture "module b" depend on a {@code @ModuleInternal} type of fixture
 * "module a" ({@link SecretInternal}). {@code ArchitectureRulesHaveTeethTest} asserts the
 * production rule fails against this, proving the gate has teeth.
 */
public class ForeignConsumer {

  private final SecretInternal secret = new SecretInternal();

  public String leak() {
    return secret.value();
  }
}
