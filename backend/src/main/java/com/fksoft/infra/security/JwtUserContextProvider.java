package com.fksoft.infra.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Real {@link UserContextProvider} (SPEC-0024 BR1/DL-0081, re-graduated in Phase 17/DL-0110):
 * resolves the current principal from the verified Spring Security context — populated by the JWT
 * bearer filter from the <strong>self-hosted IdP</strong> token (the embedded Spring Authorization
 * Server) in production/dev, and by the {@code TestSecurityConfig} actor in the {@code test}
 * profile. The port the modules consume is <strong>unchanged</strong> by the IdP swap; centralizing
 * the {@code SecurityContextHolder} access here keeps the rest of the codebase off it
 * (security.md).
 *
 * <p>Active in every profile. When there is no authentication (e.g. an unauthenticated thread), it
 * returns an anonymous context with no roles.
 */
@Component
public class JwtUserContextProvider implements UserContextProvider {

  private static final UserContext ANONYMOUS = new UserContext(null, "anonymous", Set.of());

  @Override
  public UserContext currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getName())) {
      return ANONYMOUS;
    }
    Set<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet());
    String userId = SecurityPrincipals.userId(authentication);
    return new UserContext(userId, authentication.getName(), roles);
  }
}
