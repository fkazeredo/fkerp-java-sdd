package com.fksoft.infra.security;

/**
 * Port that exposes the current {@link UserContext}. Centralizing access here keeps the rest of the
 * codebase from depending on {@code SecurityContextHolder} (security.md). The real adapter
 * (OIDC-backed) arrives with the Identity spec (SPEC-0024); until then a dev stub is used.
 */
public interface UserContextProvider {

  /** The principal bound to the current request/thread. */
  UserContext currentUser();
}
