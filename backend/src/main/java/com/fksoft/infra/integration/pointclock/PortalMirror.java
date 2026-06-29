package com.fksoft.infra.integration.pointclock;

/**
 * The <strong>external</strong> shape of a point mirror as the vendor portal exposes it
 * (SPEC-0012). This is the vendor DTO of the Anti-Corruption Layer (DL-0030): it stays in {@code
 * infra.integration.pointclock} and is <strong>never</strong> referenced by the domain (enforced by
 * an ArchUnit boundary test). Only the translated {@link
 * com.fksoft.domain.people.CollectSnapshotCommand} crosses into the domain.
 *
 * <p>It carries the portal's own field names/shape (a raw source id, the portal's period label and
 * the raw punch list). The translator ({@link PointMirrorTranslator}) maps it to domain values and
 * validates it.
 *
 * @param portalSourceId the portal's identifier for the REP/branch (external shape)
 * @param portalPeriodLabel the portal's period label (external shape, e.g. {@code "06/2026"})
 * @param punchCount the number of punches the portal reports for the period
 * @param mirrorPayload the opaque captured mirror payload (operational, stored via FileStorage)
 */
public record PortalMirror(
    String portalSourceId, String portalPeriodLabel, int punchCount, String mirrorPayload) {}
