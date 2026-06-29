package com.fksoft.infra.integration.pointclock;

import com.fksoft.domain.people.PointFailureClass;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fault-injecting {@link PointClockSource} for the resilience tests ({@code
 * simulation-and-mocking.md}): it returns scripted outcomes (a successful mirror or a classified
 * failure) so the circuit breaker, retry and dead-letter can be proven deterministically (DL-0031),
 * and it counts how many times the portal was actually hit — so a short-circuited (breaker-open)
 * call can be asserted to NOT reach the portal.
 */
public class FaultInjectingPointClockSource implements PointClockSource {

  private final Deque<Object> script = new ArrayDeque<>();
  private final AtomicInteger calls = new AtomicInteger(0);
  private int punches = 0;

  /** Scripts the next call to fail with the given class. */
  public FaultInjectingPointClockSource thenFail(PointFailureClass failureClass) {
    script.addLast(failureClass);
    return this;
  }

  /** Scripts the next call to succeed with the configured punch count. */
  public FaultInjectingPointClockSource thenSucceed(int punchCount) {
    script.addLast(punchCount);
    return this;
  }

  /** How many times the portal was actually hit (a short-circuited call does not count). */
  public int calls() {
    return calls.get();
  }

  @Override
  public PortalMirror fetchMirror(String sourceRef, String periodRef) {
    calls.incrementAndGet();
    Object next = script.pollFirst();
    if (next instanceof PointFailureClass failureClass) {
      throw new PointClockSourceException(failureClass, "injected failure: " + failureClass);
    }
    int count = next instanceof Integer scripted ? scripted : punches;
    return new PortalMirror(
        sourceRef, periodRef, count, "MIRROR|" + sourceRef + "|" + periodRef + "|punches=" + count);
  }
}
