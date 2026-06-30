package com.fksoft.infra.security;

import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development stub for {@link UserContextProvider} that returns a fixed user.
 *
 * <p><strong>STUB (traceable, behind a profile — graduated by SPEC-0024/DL-0081):</strong> real
 * authentication is now owned by the Identity module; the production/default adapter is {@link
 * JwtUserContextProvider} (reads the verified token). This stand-in is kept <strong>only for the
 * {@code dev} profile</strong> so a developer can run the app locally without logging in. It is
 * <strong>not</strong> a bean in production (no {@code dev} profile) — BR6. The {@code test}
 * profile uses the real {@code JwtUserContextProvider} over a {@code TestSecurityConfig} actor, so
 * the security layer stays mounted (not removed) and the existing tests stay green.
 */
@Component
@Profile("dev")
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
