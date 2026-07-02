package com.fksoft.infra.security;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges the local {@link AppUser} store to Spring Security so the self-hosted Authorization
 * Server can authenticate the form login (SPEC-0024 Phase 17 / DL-0112). It resolves the user by
 * username, exposes the BCrypt hash for the password check (never logged), and maps the granted
 * {@code ROLE_*} names to authorities — which the token customizer then places into {@code
 * realm_access.roles} (DL-0110). A generic error (no "user not found" detail leak) satisfies BR4.
 * Infra-only; not active in the {@code test} profile (the suite never boots the AS/form login).
 */
@Service
@Profile("!test")
public class AppUserDetailsService implements UserDetailsService {

  private final AppUserRepository users;
  private final LoginAttemptService loginAttempts;

  public AppUserDetailsService(AppUserRepository users, LoginAttemptService loginAttempts) {
    this.users = users;
    this.loginAttempts = loginAttempts;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) {
    AppUser user =
        users
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("bad credentials"));
    List<SimpleGrantedAuthority> authorities =
        user.roleNames().stream().map(SimpleGrantedAuthority::new).toList();
    // Brute-force lockout (DL-0125): a currently-locked account is presented as locked, so Spring
    // Security refuses authentication with a LockedException before the password is even checked.
    boolean locked = loginAttempts.isLocked(user.username());
    return User.withUsername(user.username())
        .password(user.passwordHash())
        .authorities(authorities)
        .disabled(!user.isActive())
        .accountLocked(locked)
        .build();
  }
}
