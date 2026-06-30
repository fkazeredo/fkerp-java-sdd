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
 * Application service for the Identity module (SPEC-0024). It authenticates a local user against
 * the BCrypt hash ({@link #login}) — the single place credentials are checked — lists the
 * role/permission catalogue ({@link #listRoles}), and records the access audit (login) reusing the
 * Platform's append-only {@code system_audit} (DL-0083).
 *
 * <p><strong>Security (BR4):</strong> a failed login (unknown user / wrong password / disabled
 * account) raises the same generic {@link InvalidCredentialsException}; no message reveals whether
 * the user exists. No password/hash/token is ever logged or carried in an event/audit detail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

  private final IdentityUserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordHasher passwordHasher;
  private final SystemAuditService auditService;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Authenticates a user by login + raw password (SPEC-0024 BR1). On success, publishes {@link
   * UserAuthenticated} and records an {@code AUTH_LOGIN} audit line; on any failure raises the
   * generic {@link InvalidCredentialsException} (BR4) — the caller maps it to a generic 401.
   *
   * @param username the login
   * @param rawPassword the submitted password
   * @return the verified principal (whose roles a JWT will carry)
   * @throws InvalidCredentialsException on unknown user, wrong password or disabled account
   */
  @Transactional
  public AuthenticatedUser login(String username, String rawPassword) {
    if (username == null || rawPassword == null || username.isBlank()) {
      throw new InvalidCredentialsException();
    }
    IdentityUser user =
        userRepository
            .findByUsername(username.trim())
            .filter(IdentityUser::isActive)
            .filter(u -> passwordHasher.matches(rawPassword, u.passwordHash()))
            .orElseThrow(InvalidCredentialsException::new);

    Instant now = clock.instant();
    auditService.record(
        AuditType.AUTH_LOGIN,
        user.username(),
        "{\"event\":\"LOGIN_SUCCESS\",\"userId\":\"" + user.id() + "\"}");
    events.publishEvent(new UserAuthenticated(user.id(), user.username(), now));
    log.info("identity.login user={} result=SUCCESS", user.username());

    return new AuthenticatedUser(user.id(), user.username(), user.displayName(), user.roleNames());
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
