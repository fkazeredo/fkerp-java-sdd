package archfixture.modulea;

import com.fksoft.domain.ModuleInternal;

/**
 * Test fixture: a {@link ModuleInternal} type belonging to fixture "module a". {@code
 * archfixture.moduleb.ForeignConsumer} deliberately depends on it from another fixture module;
 * {@code ArchitectureRulesHaveTeethTest} asserts the production rule (re-pointed at the {@code
 * archfixture.} root) fails against that, proving the cross-module encapsulation gate has teeth. It
 * lives outside {@code com.fksoft}, so the production {@link ArchitectureTest} never sees it.
 */
@ModuleInternal
public class SecretInternal {

  public String value() {
    return "module-a implementation detail";
  }
}
