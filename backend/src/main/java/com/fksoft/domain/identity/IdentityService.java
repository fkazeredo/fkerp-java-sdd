package com.fksoft.domain.identity;

import com.fksoft.domain.platform.AuditType;
import com.fksoft.domain.platform.SystemAuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Identity module (SPEC-0024 — graduated to the external IdP in Phase
 * 13, DL-0104/0107). Authentication now happens at the external OIDC IdP (Keycloak); this service
 * no longer verifies credentials. It lists the role/permission catalogue ({@link #listRoles}, the
 * local source of truth of internal authorization — BR5/BR16) and records the access audit (login
 * first touch and denials) reusing the Platform's append-only {@code system_audit} (DL-0083).
 *
 * <p><strong>Security (BR4):</strong> no token/secret is ever logged or carried in an event/audit
 * detail; only metadata (actor, action, resource).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

  private final RoleRepository roleRepository;
  private final SystemAuditService auditService;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Records the access audit of an authenticated user's first touch (SPEC-0024 BR3/DL-0083): an
   * {@code AUTH_LOGIN} line in the Platform's append-only {@code system_audit} and the {@link
   * UserAuthenticated} event (which backs the {@code acme.identity.logins} metric — DL-0098). Since
   * the login itself happens at the IdP (Phase 13), this is invoked once per session on the first
   * authenticated call ({@code GET /me}). Metadata only — never a token/secret (BR4).
   *
   * @param actor the username (from the verified token), or {@code null} when unresolved
   * @param userId the stable user id (IdP subject), or {@code null}
   */
  @Transactional
  public void recordLogin(String actor, String userId) {
    Instant now = clock.instant();
    auditService.record(
        AuditType.AUTH_LOGIN,
        actor,
        "{\"event\":\"LOGIN_SUCCESS\",\"userId\":\"" + safe(userId) + "\"}");
    events.publishEvent(new UserAuthenticated(userId, actor, now));
    log.info("identity.login user={} result=SUCCESS", actor);
  }

  /**
   * Records an access denial (SPEC-0024 BR2/BR3/DL-0083): a 403 for insufficient role. Reuses the
   * Platform's append-only {@code system_audit} ({@code ACCESS_DENIED}) and publishes the {@link
   * AccessDenied} event. The detail is metadata only — actor, action, resource — never a
   * token/secret (BR4).
   *
   * @param actor who (the username, or {@code null} when unauthenticated)
   * @param action the attempted action (HTTP method + path)
   * @param resource the targeted resource (request URI), or {@code null}
   */
  @Transactional
  public void recordAccessDenied(String actor, String action, String resource) {
    Instant now = clock.instant();
    auditService.record(
        AuditType.ACCESS_DENIED,
        actor,
        "{\"action\":\""
            + safe(action)
            + "\",\"resource\":\""
            + safe(resource)
            + "\",\"result\":\"DENY\"}");
    events.publishEvent(new AccessDenied(actor, action, resource, now));
    log.info("identity.access actor={} action={} result=DENY", actor, action);
  }

  private static String safe(String text) {
    return text == null ? "" : text.replace("\"", "'");
  }

  /** The role/permission catalogue (SPEC-0024 — {@code GET /api/identity/roles}). */
  @Transactional(readOnly = true)
  public List<RoleView> listRoles() {
    return roleRepository.findAll().stream()
        .map(role -> role.toView())
        .sorted(Comparator.comparing(RoleView::name))
        .toList();
  }
}
