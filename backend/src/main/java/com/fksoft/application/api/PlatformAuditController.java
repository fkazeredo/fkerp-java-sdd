package com.fksoft.application.api;

import com.fksoft.domain.platform.AuditType;
import com.fksoft.domain.platform.SystemAuditService;
import com.fksoft.domain.platform.SystemAuditView;
import com.fksoft.infra.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the consolidated system audit (SPEC-0023 BR4) — slice 8j-3. {@code GET
 * /api/platform/audit} returns the append-only trail filtered by actor, type and time window,
 * newest first, paginated. The detail is metadata only — never secret material (BR1).
 */
@Tag(name = "Platform Audit", description = "Auditoria de sistema (append-only)")
@RestController
@RequestMapping("/api/platform/audit")
@RequiredArgsConstructor
public class PlatformAuditController {

  private final SystemAuditService auditService;

  /** The filtered, paginated audit trail (SPEC-0023 — {@code GET /audit}). */
  @GetMapping
  public PageResponse<SystemAuditView> audit(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) AuditType type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    return PageResponse.from(auditService.search(actor, type, from, to, pageable));
  }
}
