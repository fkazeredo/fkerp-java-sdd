package com.fksoft.application.api;

import com.fksoft.application.api.dto.SystemHealthResponse;
import com.fksoft.infra.health.DatabaseHealthProbe;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System endpoints. {@code GET /api/system/health} is an unauthenticated readiness probe that
 * reflects real database connectivity (SPEC-0001): {@code 200} with {@code db: UP} when reachable,
 * {@code 503} with {@code db: DOWN} otherwise. The delivery layer may depend on infra (ADR 0012),
 * so it uses the technical {@link DatabaseHealthProbe}.
 */
@Tag(name = "System", description = "Saúde do sistema")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

  private final DatabaseHealthProbe databaseHealthProbe;

  @GetMapping("/health")
  public ResponseEntity<SystemHealthResponse> health() {
    boolean dbUp = databaseHealthProbe.databaseUp();
    SystemHealthResponse body =
        new SystemHealthResponse(dbUp ? "UP" : "DOWN", dbUp ? "UP" : "DOWN");
    return dbUp
        ? ResponseEntity.ok(body)
        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }
}
