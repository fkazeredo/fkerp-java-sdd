package com.fksoft.application.api;

import com.fksoft.application.api.dto.LoginRequest;
import com.fksoft.application.api.dto.LoginResponse;
import com.fksoft.application.api.dto.MeResponse;
import com.fksoft.domain.identity.AuthenticatedUser;
import com.fksoft.domain.identity.IdentityService;
import com.fksoft.domain.identity.RoleView;
import com.fksoft.domain.platform.AuditType;
import com.fksoft.domain.platform.SystemAuditService;
import com.fksoft.domain.platform.SystemAuditView;
import com.fksoft.infra.security.JwtIssuer;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Identity module (SPEC-0024). {@code POST /login} (public) authenticates a
 * local user and returns a bearer JWT; {@code GET /me} returns the current principal from the
 * verified token; {@code GET /roles} lists the role/permission catalogue; {@code GET /access-audit}
 * reads the access trail (login/denial) from the Platform's append-only {@code system_audit}
 * (DL-0083). The delivery layer mints the token from the authenticated user; the backend is the
 * authorization authority (security.md).
 */
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityController {

  private final IdentityService identityService;
  private final JwtIssuer jwtIssuer;
  private final UserContextProvider userContextProvider;
  private final SystemAuditService auditService;

  /** Authenticates and returns a bearer token (public). Invalid credentials → generic 401 (BR4). */
  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    AuthenticatedUser user = identityService.login(request.username(), request.password());
    String token = jwtIssuer.issue(user);
    return new LoginResponse(token, "Bearer", jwtIssuer.ttlSeconds(), MeResponse.from(user));
  }

  /** The current principal resolved from the verified token (BR1). */
  @GetMapping("/me")
  public MeResponse me() {
    return MeResponse.from(userContextProvider.currentUser());
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
