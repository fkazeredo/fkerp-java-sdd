package com.fksoft.application.api.dto;

/**
 * Application build metadata (entity-free transport DTO, SPEC-0027/DL-0097). {@code version}
 * follows SemVer {@code MAJOR.MINOR.PATCH} (the pom is the source of truth — ADR 0015); {@code
 * gitCommit} is the abbreviated commit id; {@code buildTime} is the packaging instant (ISO-8601).
 * When the running artifact has no packaged build-info / git.properties (e.g. a dev or test run),
 * the unavailable fields degrade to a stable {@code "unknown"} marker — the endpoint never fails
 * (BR4).
 *
 * <p>Carries only build metadata — never a secret — so it is served on a public endpoint (BR2).
 */
public record VersionResponse(String version, String gitCommit, String buildTime) {}
