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

  private static final UserContext DEV_USER =
      new UserContext("dev-user", "dev", Set.of("ROLE_DEV"));

  @Override
  public UserContext currentUser() {
    return DEV_USER;
  }
}
