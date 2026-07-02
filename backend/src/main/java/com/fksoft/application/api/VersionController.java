package com.fksoft.application.api;

import com.fksoft.application.api.dto.VersionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint exposing the running build's metadata (SPEC-0027/DL-0097) so the UI can show it
 * (footer / about): {@code { version, gitCommit, buildTime }}. No authentication required — it
 * carries only build metadata, never a secret (BR2).
 *
 * <p>The version is taken from the packaged {@link BuildProperties} when present, otherwise from
 * the {@code app.version} property filtered from the pom at build time (the SemVer source of truth
 * — ADR 0015). The git commit and build time come from {@link GitProperties}/{@link
 * BuildProperties}; when the artifact has no packaged build-info / git.properties (a dev or test
 * run), those fields degrade to a stable {@code "unknown"} marker rather than failing the endpoint
 * (BR4). Both info beans are optional ({@link ObjectProvider}) precisely so the endpoint works in
 * every runtime.
 */
@Tag(name = "Version", description = "Metadados de versão/build")
@RestController
public class VersionController {

  private static final String UNKNOWN = "unknown";

  private final ObjectProvider<BuildProperties> buildProperties;
  private final ObjectProvider<GitProperties> gitProperties;
  private final String configuredVersion;

  public VersionController(
      ObjectProvider<BuildProperties> buildProperties,
      ObjectProvider<GitProperties> gitProperties,
      @Value("${app.version:unknown}") String configuredVersion) {
    this.buildProperties = buildProperties;
    this.gitProperties = gitProperties;
    this.configuredVersion = configuredVersion;
  }

  /**
   * The running build's metadata. Always returns 200 with a fully-populated payload, degrading
   * absent fields to {@code "unknown"} (BR4).
   *
   * @return the version payload
   */
  @GetMapping("/api/version")
  public VersionResponse version() {
    return new VersionResponse(resolveVersion(), resolveGitCommit(), resolveBuildTime());
  }

  private String resolveVersion() {
    BuildProperties build = buildProperties.getIfAvailable();
    if (build != null && hasText(build.getVersion())) {
      return build.getVersion();
    }
    return hasText(configuredVersion) ? configuredVersion : UNKNOWN;
  }

  private String resolveGitCommit() {
    GitProperties git = gitProperties.getIfAvailable();
    if (git != null && hasText(git.getShortCommitId())) {
      return git.getShortCommitId();
    }
    return UNKNOWN;
  }

  private String resolveBuildTime() {
    BuildProperties build = buildProperties.getIfAvailable();
    if (build != null && build.getTime() != null) {
      return build.getTime().toString();
    }
    return UNKNOWN;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
