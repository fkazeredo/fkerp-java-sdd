package com.fksoft.infra.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Technical probe that verifies real database connectivity by issuing a trivial {@code SELECT 1}.
 * Used by the readiness endpoint so {@code GET /api/system/health} reflects whether the app can
 * actually reach the database (SPEC-0001 business rule), not just that the process is alive.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthProbe {

  private final JdbcTemplate jdbcTemplate;

  /** {@code true} when the database answers a probe query; {@code false} (logged) otherwise. */
  public boolean databaseUp() {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return true;
    } catch (DataAccessException ex) {
      log.warn("Database readiness probe failed: {}", ex.getMessage());
      return false;
    }
  }
}
