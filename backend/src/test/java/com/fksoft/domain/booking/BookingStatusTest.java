package com.fksoft.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the booking state machine (SPEC-0006 BR2): valid and invalid transitions. */
class BookingStatusTest {

  @Test
  void allowsTheValidLifecycleTransitions() {
    assertThat(BookingStatus.ORDERED.canTransitionTo(BookingStatus.PENDING)).isTrue();
    assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
    assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
    assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.COMPLETED)).isTrue();
    assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.NO_SHOW)).isTrue();
    assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CHANGED)).isTrue();
    assertThat(BookingStatus.CHANGED.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
  }

  @Test
  void rejectsInvalidTransitions() {
    assertThat(BookingStatus.ORDERED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    assertThat(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    assertThat(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    assertThat(BookingStatus.NO_SHOW.canTransitionTo(BookingStatus.COMPLETED)).isFalse();
  }
}
