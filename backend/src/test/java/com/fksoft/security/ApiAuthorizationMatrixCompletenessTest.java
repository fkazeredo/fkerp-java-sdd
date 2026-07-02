package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.infra.security.ApiAuthorizationMatrix;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Fitness function of the authorization matrix (SPEC-0024 Phase 19a, DL-0119) — the same
 * registry-plus-completeness pattern as the {@code HttpErrorMapping} test:
 *
 * <ol>
 *   <li><strong>No unmapped write:</strong> every {@code POST/PUT/PATCH/DELETE} endpoint under
 *       {@code /api/**} must be covered by a rule of {@link ApiAuthorizationMatrix}. The security
 *       chain default-denies unmapped writes, so forgetting the matrix entry makes the endpoint
 *       unreachable — this test names the offender instead of letting it 403 mysteriously.
 *   <li><strong>No stale rule:</strong> every write rule must match at least one real endpoint, so
 *       the matrix cannot silently drift from the actual API surface.
 * </ol>
 */
class ApiAuthorizationMatrixCompletenessTest extends AbstractPostgresIntegrationTest {

  private static final Set<HttpMethod> WRITE_METHODS =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

  private static final PathPatternParser PARSER = new PathPatternParser();

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private record Endpoint(HttpMethod method, String path) {}

  @Test
  void everyApiWriteEndpointIsCoveredByTheAuthorizationMatrix() {
    List<Endpoint> unmapped =
        apiEndpoints().stream()
            .filter(endpoint -> WRITE_METHODS.contains(endpoint.method()))
            .filter(endpoint -> !isCovered(endpoint))
            .toList();

    assertThat(unmapped)
        .withFailMessage(
            "Write endpoints NOT covered by the ApiAuthorizationMatrix (they are default-denied; "
                + "add a rule with the owning role — DL-0119): %s",
            unmapped)
        .isEmpty();
  }

  @Test
  void everyWriteRuleOfTheMatrixMatchesAtLeastOneRealEndpoint() {
    List<Endpoint> endpoints = apiEndpoints();
    List<ApiAuthorizationMatrix.Rule> stale =
        ApiAuthorizationMatrix.RULES.stream()
            .filter(ApiAuthorizationMatrix.Rule::isWriteRule)
            .filter(
                rule ->
                    endpoints.stream()
                        .noneMatch(
                            endpoint ->
                                endpoint.method() == rule.method()
                                    && matches(rule.pattern(), endpoint.path())))
            .toList();

    assertThat(stale)
        .withFailMessage(
            "Matrix write rules matching NO real endpoint (stale — remove or fix the pattern): %s",
            stale)
        .isEmpty();
  }

  private boolean isCovered(Endpoint endpoint) {
    return ApiAuthorizationMatrix.RULES.stream()
        .anyMatch(
            rule -> rule.method() == endpoint.method() && matches(rule.pattern(), endpoint.path()));
  }

  /** All concrete endpoints under {@code /api/} declared by the MVC handler mapping. */
  private List<Endpoint> apiEndpoints() {
    return handlerMapping.getHandlerMethods().keySet().stream()
        .flatMap(
            info ->
                info.getPathPatternsCondition().getPatterns().stream()
                    .map(pattern -> pattern.getPatternString())
                    .filter(path -> path.startsWith("/api/"))
                    .flatMap(
                        path ->
                            info.getMethodsCondition().getMethods().stream()
                                .map(RequestMethod::asHttpMethod)
                                .map(method -> new Endpoint(method, path))))
        .distinct()
        .toList();
  }

  /**
   * Matches a matrix pattern against an endpoint's declared path template. Template variables
   * ({@code {id}}) are literal segments here, which the rule wildcards ({@code *}, {@code **})
   * cover — the same semantics the runtime PathPattern matching applies to real paths.
   */
  private static boolean matches(String rulePattern, String endpointPath) {
    return PARSER.parse(rulePattern).matches(PathContainer.parsePath(endpointPath));
  }
}
