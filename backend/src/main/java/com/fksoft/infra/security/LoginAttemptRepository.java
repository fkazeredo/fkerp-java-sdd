package com.fksoft.infra.security;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Repository of the per-username login-attempt counter (SPEC-0024 — Fase 19c, DL-0125). */
interface LoginAttemptRepository extends Repository<LoginAttempt, String> {

  Optional<LoginAttempt> findByUsername(String username);

  LoginAttempt save(LoginAttempt attempt);
}
