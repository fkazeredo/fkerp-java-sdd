package com.fksoft.infra.integration.pointclock;

/**
 * Outbound port to the vendor point-clock portal (SPEC-0012; {@code messaging-and-integrations.md}
 * §External integrations). The real portal is out of scope of this phase, so the production binding
 * is a <strong>traceable mock</strong> ({@code simulation-and-mocking.md}); the tests inject a
 * fault-injecting implementation to prove the circuit breaker, retry and dead-letter
 * deterministically (DL-0031). The implementation MUST apply a per-call timeout and credentials are
 * custodied by the Platform (never in code/log — BR1, security.md).
 */
public interface PointClockSource {

  /**
   * Fetches the operational mirror (punches/journey) for a source and period from the portal.
   *
   * @param sourceRef the REP/branch reference to collect
   * @param periodRef the period to collect ({@code YYYY-MM})
   * @return the external portal mirror (vendor shape; translated by the ACL, never leaked to
   *     domain)
   * @throws PointClockSourceException when the outbound call fails (carries the failure class)
   */
  PortalMirror fetchMirror(String sourceRef, String periodRef);
}
