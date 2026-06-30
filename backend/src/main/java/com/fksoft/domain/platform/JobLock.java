package com.fksoft.domain.platform;

/**
 * Port for a distributed job lock (SPEC-0023 BR2; DL-0075). The adapter ({@code
 * infra.platform.PostgresAdvisoryJobLock}) uses a Postgres advisory lock so at most one instance
 * runs a given job at a time, even across nodes — without an extra lock table to clean up. The
 * domain depends only on this port, so the lock mechanism can be swapped later without touching the
 * model.
 */
public interface JobLock {

  /**
   * Runs {@code work} while holding the named job's lock; if the lock is already held, throws
   * {@link JobLockedException} instead of waiting (one instance at a time, BR2).
   *
   * @param jobName the job to lock on
   * @param work the work to run under the lock
   * @param <T> the work result type
   * @return the work result
   * @throws JobLockedException when another instance already holds the lock
   */
  <T> T runExclusively(String jobName, java.util.function.Supplier<T> work);
}
