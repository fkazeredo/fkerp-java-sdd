package com.fksoft.application.api;

import com.fksoft.application.api.dto.MeResponse;
import com.fksoft.domain.identity.IdentityService;
import com.fksoft.domain.identity.RoleView;
import com.fksoft.domain.platform.AuditType;
import com.fksoft.domain.platform.SystemAuditService;
import com.fksoft.domain.platform.SystemAuditView;
import com.fksoft.infra.security.UserContext;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Identity module (SPEC-0024 — re-graduated to the self-hosted IdP in Phase
 * 17, ADR-0018/DL-0110). Login now happens at the app's own embedded OIDC Authorization Server
 * (Authorization Code + PKCE, form {@code /login}); the in-house {@code POST /api/identity/login}
 * of the 8k was removed in Phase 13 (breaking — BR14) and stays removed. What remains:
 *
 * <ul>
 *   <li>{@code GET /me} — the current principal resolved from the verified IdP token; it is the
 *       frontend's post-login session bootstrap, so it records the {@code AUTH_LOGIN} access audit
 *       (DL-0083) once per login.
 *   <li>{@code GET /roles} — the local role/permission catalogue (the source of truth of internal
 *       authorization — BR16).
 *   <li>{@code GET /access-audit} — the access trail (login/denial) read from the Platform's
 *       append-only {@code system_audit}.
 * </ul>
 *
 * <p>The backend is the authorization authority (security.md); the frontend only mirrors the
 * token's user/roles for display and routing.
 */
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityController {

  private final IdentityService identityService;
  private final UserContextProvider userContextProvider;
  private final SystemAuditService auditService;

  /**
   * The current principal resolved from the verified IdP token (BR1). As the frontend's post-login
   * identity bootstrap, it records the {@code AUTH_LOGIN} access audit (BR3/DL-0083) for the
   * authenticated actor; anonymous calls are not audited.
   */
  @GetMapping("/me")
  public MeResponse me() {
    UserContext context = userContextProvider.currentUser();
    if (context.userId() != null || !"anonymous".equals(context.username())) {
      identityService.recordLogin(context.username(), context.userId());
    }
    return MeResponse.from(context);
  }

  /** The role/permission catalogue (authorization: {@code identity:role:read}). */
  @GetMapping("/roles")
  public List<RoleView> roles() {
    return identityService.listRoles();
  }

  /**
   * The access-audit trail (SPEC-0024 — login/denial), read from the Platform {@code system_audit}
   * (DL-0083), newest first, paginated. Authorization: {@code identity:audit:read}.
   */
  @GetMapping("/access-audit")
  public PageResponse<SystemAuditView> accessAudit(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) AuditType type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    if (type != null) {
      return PageResponse.from(auditService.search(actor, type, from, to, pageable));
    }
    // No type filter: union of the access-related types (AUTH_LOGIN + ACCESS_DENIED +
    // SECURITY_EVENT).
    List<SystemAuditView> items =
        java.util.stream.Stream.of(
                AuditType.AUTH_LOGIN, AuditType.ACCESS_DENIED, AuditType.SECURITY_EVENT)
            .flatMap(
                t ->
                    auditService
                        .search(actor, t, from, to, PageRequest.of(0, 1000))
                        .getContent()
                        .stream())
            .sorted(java.util.Comparator.comparing(SystemAuditView::occurredAt).reversed())
            .toList();
    int fromIndex = Math.min(page * pageable.getPageSize(), items.size());
    int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
    Page<SystemAuditView> pageView =
        new PageImpl<>(items.subList(fromIndex, toIndex), pageable, items.size());
    return PageResponse.from(pageView);
  }
}
