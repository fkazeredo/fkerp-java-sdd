package archfixture.infra;

/**
 * Test fixture standing in for an infra type. Lives outside {@code com.fksoft} so the production
 * architecture import never sees it; used only by {@code ArchitectureRulesHaveTeethTest}.
 */
public class SomeInfraType {

  public String value() {
    return "infra";
  }
}
