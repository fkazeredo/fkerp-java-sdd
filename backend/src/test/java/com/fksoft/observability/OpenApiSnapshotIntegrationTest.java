package com.fksoft.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract snapshot / drift gate for the OpenAPI document (SPEC-0024 Fase 19d, DL-0126). The
 * generated {@code /v3/api-docs} is normalized (pretty, key-sorted) and compared to the committed
 * snapshot at {@code docs/api/openapi.json}. A change to the API contract that is not reflected in
 * the committed snapshot fails this test — the same drift-guard idea as the Fase 18 conversion
 * invariant, generalized to the whole surface.
 *
 * <p>To regenerate the snapshot after an intentional contract change, run with {@code
 * -Dopenapi.snapshot.write=true}: the test rewrites the file and passes, and the new snapshot is
 * committed in the same slice that changed the contract.
 */
class OpenApiSnapshotIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final Path SNAPSHOT = Path.of("..", "docs", "api", "openapi.json");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void theGeneratedContractMatchesTheCommittedSnapshot() throws Exception {
    String generated = normalize(restTemplate.getForObject("/v3/api-docs", String.class));

    if (Boolean.getBoolean("openapi.snapshot.write")) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, generated, StandardCharsets.UTF_8);
      return;
    }

    assertThat(Files.exists(SNAPSHOT))
        .withFailMessage(
            "OpenAPI snapshot %s is missing — generate it with -Dopenapi.snapshot.write=true",
            SNAPSHOT.toAbsolutePath())
        .isTrue();
    String committed = normalize(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    assertThat(generated)
        .withFailMessage(
            "The OpenAPI contract changed but %s was not updated. If the change is intentional, "
                + "regenerate with -Dopenapi.snapshot.write=true and commit the snapshot in the "
                + "same slice.",
            SNAPSHOT)
        .isEqualTo(committed);
  }

  /**
   * Pretty-prints with sorted keys so the snapshot is stable and diff-friendly, dropping the
   * volatile {@code servers} block (springdoc infers it from the random test port).
   */
  @SuppressWarnings("unchecked")
  private static String normalize(String json) {
    JsonMapper mapper =
        JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    Object tree = mapper.readValue(json, Object.class);
    if (tree instanceof Map<?, ?> map) {
      ((Map<String, Object>) map).remove("servers");
    }
    return mapper.writeValueAsString(tree);
  }
}
