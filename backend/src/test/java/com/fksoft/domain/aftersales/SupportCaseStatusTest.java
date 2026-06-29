package com.fksoft.domain.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the support case state machine (SPEC-0018 Scope/Validation Rules): valid and
 * invalid transitions, and the terminal-status predicate the SLA sweep uses.
 */
class SupportCaseStatusTest {

  @Test
  void allowsTheValidLifecycleTransitions() {
    assertThat(SupportCaseStatus.OPEN.canTransitionTo(SupportCaseStatus.IN_PROGRESS)).isTrue();
    assertThat(SupportCaseStatus.OPEN.canTransitionTo(SupportCaseStatus.RESOLVED)).isTrue();
    assertThat(SupportCaseStatus.IN_PROGRESS.canTransitionTo(SupportCaseStatus.WAITING)).isTrue();
    assertThat(SupportCaseStatus.IN_PROGRESS.canTransitionTo(SupportCaseStatus.RESOLVED)).isTrue();
    assertThat(SupportCaseStatus.WAITING.canTransitionTo(SupportCaseStatus.IN_PROGRESS)).isTrue();
    assertThat(SupportCaseStatus.WAITING.canTransitionTo(SupportCaseStatus.RESOLVED)).isTrue();
    assertThat(SupportCaseStatus.RESOLVED.canTransitionTo(SupportCaseStatus.CLOSED)).isTrue();
    // Reopening: RESOLVED back to IN_PROGRESS is allowed (a cost-to-serve signal).
    assertThat(SupportCaseStatus.RESOLVED.canTransitionTo(SupportCaseStatus.IN_PROGRESS)).isTrue();
  }

  @Test
  void rejectsInvalidTransitions() {
    assertThat(SupportCaseStatus.OPEN.canTransitionTo(SupportCaseStatus.WAITING)).isFalse();
    assertThat(SupportCaseStatus.OPEN.canTransitionTo(SupportCaseStatus.CLOSED)).isFalse();
    assertThat(SupportCaseStatus.WAITING.canTransitionTo(SupportCaseStatus.CLOSED)).isFalse();
    assertThat(SupportCaseStatus.CLOSED.canTransitionTo(SupportCaseStatus.IN_PROGRESS)).isFalse();
    assertThat(SupportCaseStatus.CLOSED.canTransitionTo(SupportCaseStatus.RESOLVED)).isFalse();
    assertThat(SupportCaseStatus.RESOLVED.canTransitionTo(SupportCaseStatus.WAITING)).isFalse();
  }

  @Test
  void onlyResolvedAndClosedAreTerminalForSla() {
    assertThat(SupportCaseStatus.RESOLVED.isTerminal()).isTrue();
    assertThat(SupportCaseStatus.CLOSED.isTerminal()).isTrue();
    assertThat(SupportCaseStatus.OPEN.isTerminal()).isFalse();
    assertThat(SupportCaseStatus.IN_PROGRESS.isTerminal()).isFalse();
    assertThat(SupportCaseStatus.WAITING.isTerminal()).isFalse();
  }
}
