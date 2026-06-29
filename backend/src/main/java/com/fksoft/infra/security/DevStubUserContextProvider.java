package com.fksoft.infra.security;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Development stub for {@link UserContextProvider} that returns a fixed user.
 *
 * <p><strong>STUB (traceable, replaceable):</strong> real authentication (login, roles, OIDC) is
 * owned by the Identity spec <strong>SPEC-0024</strong>. This stand-in exists only so the
 * foundation has a working {@code UserContextProvider}; it is replaced by the real adapter when
 * SPEC-0024 is implemented (simulation-and-mocking.md). It must never ship as the production
 * identity source.
 */
@Component
public class DevStubUserContextProvider implements UserContextProvider {

  // Roles include ROLE_DIRECTOR/ROLE_POLICY_ADMIN so CommercialPolicy's runtime self-service
  // (SPEC-0014, DL-0038) is exercisable in dev/tests until the real IdP (SPEC-0024) lands. Tests
  // that
  // assert the 403 path inject a roleless context directly into the service (not via this stub).
  private static final UserContext DEV_USER =
      new UserContext("dev-user", "dev", Set.of("ROLE_DEV", "ROLE_DIRECTOR", "ROLE_POLICY_ADMIN"));

  @Override
  public UserContext currentUser() {
    return DEV_USER;
  }
}
