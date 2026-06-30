package com.fksoft.infra.platform;

import com.fksoft.domain.platform.JobLock;
import com.fksoft.domain.platform.JobLockedException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Postgres advisory-lock adapter for the {@link JobLock} port (SPEC-0023 BR2; DL-0075). It uses a
 * <strong>session-level</strong> advisory lock ({@code pg_try_advisory_lock} / {@code
 * pg_advisory_unlock}) so the lock spans the job's multiple (REQUIRES_NEW) run-record transactions,
 * guaranteeing one instance at a time even across nodes — without a lock table to manage. If the
 * lock is already held, it throws {@link JobLockedException} immediately (no waiting). The lock key
 * is a stable hash of the job name.
 *
 * <p>Because the lock is session-scoped and Hikari pools connections, the acquire and release MUST
 * run on the same physical connection — so the whole try/work/unlock is executed inside a single
 * {@code ConnectionCallback}, holding one connection for the job's duration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresAdvisoryJobLock implements JobLock {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public <T> T runExclusively(String jobName, Supplier<T> work) {
    long lockKey = lockKey(jobName);
    return jdbcTemplate.execute(
        (org.springframework.jdbc.core.ConnectionCallback<T>)
            connection -> {
              boolean acquired;
              try (var ps = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                ps.setLong(1, lockKey);
                try (var rs = ps.executeQuery()) {
                  rs.next();
                  acquired = rs.getBoolean(1);
                }
              }
              if (!acquired) {
                log.info("JobLockBusy job={} (another instance holds the lock)", jobName);
                throw new JobLockedException(jobName);
              }
              try {
                return work.get();
              } finally {
                try (var ps = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                  ps.setLong(1, lockKey);
                  ps.execute();
                }
              }
            });
  }

  /** A stable 64-bit lock key for a job name (FNV-1a over the bytes). */
  private static long lockKey(String jobName) {
    long hash = 0xcbf29ce484222325L;
    for (byte b : jobName.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
      hash ^= (b & 0xff);
      hash *= 0x100000001b3L;
    }
    return hash;
  }
}
