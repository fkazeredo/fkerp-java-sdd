package com.fksoft.infra.integration.pointclock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Traceable production binding of the {@link PointClockSource} (SPEC-0012; {@code
 * simulation-and-mocking.md}): the real vendor portal is out of scope of this phase, so this
 * adapter returns a deterministic operational mirror that speaks the documented external contract.
 * It is the explicit, traceable stand-in for the live integration — not fake business logic. Tests
 * supply a {@code @Primary} fault-injecting {@link PointClockSource} to exercise the
 * breaker/retry/dead-letter.
 */
@Slf4j
@Component
public class SimulatedPointClockSource implements PointClockSource {

  @Override
  public PortalMirror fetchMirror(String sourceRef, String periodRef) {
    // Deterministic operational mirror (the live portal client is out of scope — DL-0029/0031).
    log.info(
        "PointClockSource fetch sourceRef={} periodRef={} (simulated portal)",
        sourceRef,
        periodRef);
    String payload = "MIRROR|" + sourceRef + "|" + periodRef + "|punches=0";
    return new PortalMirror(sourceRef, periodRef, 0, payload);
  }
}
