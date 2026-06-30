package com.fksoft.domain.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ContractedJourney} value object and {@link TimeFormat} (SPEC-0022 BR1).
 * Proves the {@code HH:mm} parsing/rendering and the invariant (positive, within a day), and the
 * signed time-bank formatting used by the views.
 */
class ContractedJourneyTest {

  @Test
  void parsesAndRendersHhMm() {
    assertThat(ContractedJourney.parse("08:00").minutes()).isEqualTo(480);
    assertThat(ContractedJourney.parse("06:30").minutes()).isEqualTo(390);
    assertThat(new ContractedJourney(480).toLabel()).isEqualTo("08:00");
    assertThat(new ContractedJourney(390).toLabel()).isEqualTo("06:30");
  }

  @Test
  void rejectsMalformedOrOutOfRangeJourney() {
    assertThatThrownBy(() -> ContractedJourney.parse(null))
        .isInstanceOf(EmployeeInvalidException.class);
    assertThatThrownBy(() -> ContractedJourney.parse("8h"))
        .isInstanceOf(EmployeeInvalidException.class);
    assertThatThrownBy(() -> ContractedJourney.parse("08:60"))
        .isInstanceOf(EmployeeInvalidException.class);
    assertThatThrownBy(() -> new ContractedJourney(0)).isInstanceOf(EmployeeInvalidException.class);
    assertThatThrownBy(() -> new ContractedJourney(24 * 60 + 1))
        .isInstanceOf(EmployeeInvalidException.class);
  }

  @Test
  void formatsSignedTimeBankBalance() {
    assertThat(TimeFormat.signedHhmm(20)).isEqualTo("+00:20");
    assertThat(TimeFormat.signedHhmm(-70)).isEqualTo("-01:10");
    assertThat(TimeFormat.signedHhmm(0)).isEqualTo("+00:00");
    assertThat(TimeFormat.hhmm(176 * 60 + 20)).isEqualTo("176:20");
  }
}
