package com.fksoft.domain.people;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit/regression tests for the operational point snapshot (SPEC-0012 BR3/BR5). The keystone
 * regression: a collected snapshot is <strong>always</strong> operational only — it can never be
 * treated as the legal AFD/AEJ document. Re-collecting refreshes in place (idempotency) without
 * changing identity.
 */
class PointSnapshotTest {

  private static final Instant T0 = Instant.parse("2026-06-26T03:10:00Z");
  private static final Instant T1 = Instant.parse("2026-06-27T03:10:00Z");

  @Test
  void aCollectedSnapshotIsAlwaysOperationalOnly() {
    PointSnapshot snapshot =
        PointSnapshot.collect("REP-FILIAL-SP", "2026-06", "mirror-ref", 482, T0);

    // BR3 regression: the operational mirror is NEVER a legal document. This invariant is what
    // stops
    // a consumer from treating the scraped snapshot as the retention artifact (that is the AFD in
    // the
    // Compliance vault).
    assertThat(snapshot.toView().operationalOnly()).isTrue();
    assertThat(snapshot.toView().marks()).isEqualTo(482);
    assertThat(snapshot.toView().sourceRef()).isEqualTo("REP-FILIAL-SP");
    assertThat(snapshot.toView().periodRef()).isEqualTo("2026-06");
    assertThat(snapshot.toView().collectedAt()).isEqualTo(T0);
  }

  @Test
  void refreshUpdatesContentInPlaceKeepingIdentityAndOperationalFlag() {
    PointSnapshot snapshot =
        PointSnapshot.collect("REP-FILIAL-SP", "2026-06", "mirror-ref-1", 100, T0);
    var idBefore = snapshot.id();

    snapshot.refresh("mirror-ref-2", 482, T1);

    assertThat(snapshot.id()).isEqualTo(idBefore); // same identity (BR5 idempotency)
    assertThat(snapshot.toView().marks()).isEqualTo(482);
    assertThat(snapshot.toView().collectedAt()).isEqualTo(T1);
    assertThat(snapshot.toView().operationalOnly()).isTrue(); // still operational only
  }
}
