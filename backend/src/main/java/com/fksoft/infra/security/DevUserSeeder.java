package com.fksoft.infra.security;

import com.fksoft.domain.identity.IdentityUser;
import com.fksoft.domain.identity.IdentityUserRepository;
import java.time.Clock;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds one dev/test user per base role (SPEC-0024/DL-0080/DL-0081), so login works out of the box
 * in local development and so the integration tests can mint a real token. Runs
 * <strong>only</strong> in the {@code dev}/{@code test} profiles — never in production (BR6).
 * Idempotent: it skips users that already exist. Passwords are encoded with the {@link
 * PasswordEncoder} — no plaintext/hardcoded hash (BR4). The shared dev password is intentionally
 * weak and exists only for local/test convenience.
 */
@Slf4j
@Component
@Profile({"dev", "test"})
public class DevUserSeeder implements ApplicationRunner {

  /** Weak, well-known password — dev/test ONLY. Never a production credential. */
  public static final String DEV_PASSWORD = "dev12345";

  private final IdentityUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public DevUserSeeder(
      IdentityUserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    seed("director", "Diana Diretora", Set.of("ROLE_DIRECTOR"));
    seed("finance", "Fabio Financeiro", Set.of("ROLE_FINANCE"));
    seed("ops", "Olivia Operacional", Set.of("ROLE_OPERATIONS"));
    seed("it", "Ivo TI", Set.of("ROLE_IT"));
    seed("policy", "Paula Curadora", Set.of("ROLE_POLICY_ADMIN"));
    seed("viewer", "Vera Leitora", Set.of("ROLE_VIEWER"));
    // A convenience super-user for local dev: all base roles (still dev/test only).
    seed(
        "dev",
        "Dev Admin",
        Set.of(
            "ROLE_DIRECTOR",
            "ROLE_FINANCE",
            "ROLE_OPERATIONS",
            "ROLE_IT",
            "ROLE_POLICY_ADMIN",
            "ROLE_VIEWER"));
  }

  private void seed(String username, String displayName, Set<String> roles) {
    if (userRepository.existsByUsername(username)) {
      return;
    }
    userRepository.save(
        IdentityUser.create(
            username, passwordEncoder.encode(DEV_PASSWORD), displayName, roles, clock.instant()));
    log.info("identity.seed user={} roles={} (dev/test only)", username, roles);
  }
}
