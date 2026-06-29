package com.fksoft.domain.people;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the point-failure classification (SPEC-0012 BR2): which classes are retryable. The
 * class drives whether the crawler retries or dead-letters; authentication failures must NOT be
 * retried in a loop ({@code messaging-and-integrations.md}).
 */
class PointFailureClassTest {

  @Test
  void transientNetworkFailuresAreRetryable() {
    assertThat(PointFailureClass.TIMEOUT.retryable()).isTrue();
    assertThat(PointFailureClass.UNAVAILABLE.retryable()).isTrue();
  }

  @Test
  void fatalFailuresAreNotRetryable() {
    assertThat(PointFailureClass.AUTHENTICATION_FAILED.retryable()).isFalse();
    assertThat(PointFailureClass.INVALID_RESPONSE.retryable()).isFalse();
    assertThat(PointFailureClass.UNKNOWN_ERROR.retryable()).isFalse();
  }
}
