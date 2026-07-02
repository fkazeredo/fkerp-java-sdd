package com.fksoft.infra.security;

import java.time.Clock;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds one dev/E2E user per base role plus a super-user {@code dev} (SPEC-0024 Phase 17 /
 * DL-0112), so the self-hosted Authorization Server has users to authenticate out of the box in
 * local development and in the isolated E2E stack. Runs <strong>only</strong> in the {@code
 * dev}/{@code e2e} profiles — never in production (BR6), and not in {@code test} (the suite never
 * boots the AS). Idempotent: it skips users that already exist. Passwords are encoded with the
 * {@link PasswordEncoder} — no plaintext/hardcoded hash (BR4). The shared dev password is
 * intentionally weak and exists only for local/E2E convenience. It mirrors the users the Keycloak
 * realm export used to seed (DL-0103), so login journeys and role checks stay deterministic.
 */
@Slf4j
@Component
@Profile({"dev", "e2e"})
public class DevUserSeeder implements ApplicationRunner {

  /** Weak, well-known password — dev/E2E ONLY. Never a production credential. */
  public static final String DEV_PASSWORD = "dev12345";

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public DevUserSeeder(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
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
    // A convenience super-user for local dev: all base roles (still dev/E2E only).
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
    if (users.existsByUsername(username)) {
      return;
    }
    // Even the dev seed password passes the minimal policy (DL-0125): a weak password never reaches
    // the store, keeping the seeder honest about what a real user-creation flow must enforce.
    PasswordPolicy.validate(DEV_PASSWORD);
    users.save(
        AppUser.of(
            username, passwordEncoder.encode(DEV_PASSWORD), displayName, roles, clock.instant()));
    log.info("identity.seed user={} roles={} (dev/E2E only)", username, roles);
  }
}
