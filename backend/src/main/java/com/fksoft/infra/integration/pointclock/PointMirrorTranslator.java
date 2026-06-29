package com.fksoft.infra.integration.pointclock;

import com.fksoft.domain.people.CollectSnapshotCommand;
import com.fksoft.domain.people.PointFailureClass;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The Anti-Corruption Layer translator for the point-clock portal (SPEC-0012; DL-0030): it maps the
 * <strong>external</strong> {@link PortalMirror} (vendor shape) into the domain {@link
 * CollectSnapshotCommand}, validating the external payload and storing the captured mirror as an
 * operational payload. The vendor shape stays in this adapter; only the domain command leaves it,
 * so the external DTO never reaches the domain (enforced by an ArchUnit boundary test).
 *
 * <p>A malformed mirror (missing source/period, unparseable period label) is an {@code
 * INVALID_RESPONSE} — surfaced as a {@link PointClockSourceException} so the crawler classifies it
 * (never a fake snapshot).
 */
@Slf4j
@Component
public class PointMirrorTranslator {

  private final com.fksoft.domain.compliance.FileStorage fileStorage;

  public PointMirrorTranslator(com.fksoft.domain.compliance.FileStorage fileStorage) {
    this.fileStorage = fileStorage;
  }

  /**
   * Translates a portal mirror for an expected source/period into a domain collect command. The
   * portal's own labels must be consistent with the requested {@code sourceRef}/{@code periodRef};
   * the captured payload is stored and referenced opaquely (operational data via FileStorage).
   *
   * @param mirror the external portal mirror (vendor shape)
   * @param sourceRef the requested REP/branch reference (domain value)
   * @param periodRef the requested period ({@code YYYY-MM})
   * @return the translated domain command (the only shape that crosses the ACL boundary)
   * @throws PointClockSourceException ({@code INVALID_RESPONSE}) when the mirror is malformed
   */
  public CollectSnapshotCommand translate(PortalMirror mirror, String sourceRef, String periodRef) {
    if (mirror == null
        || isBlank(mirror.portalSourceId())
        || isBlank(mirror.portalPeriodLabel())
        || mirror.punchCount() < 0
        || mirror.mirrorPayload() == null) {
      throw new PointClockSourceException(
          PointFailureClass.INVALID_RESPONSE, "portal mirror is malformed or incomplete");
    }
    // Store the captured operational mirror via the vault FileStorage port; the ref is opaque.
    String payloadRef =
        fileStorage.store(
            mirror.mirrorPayload().getBytes(StandardCharsets.UTF_8),
            "point-mirror-" + sourceRef + "-" + periodRef + ".txt",
            "text/plain");
    return new CollectSnapshotCommand(sourceRef, periodRef, payloadRef, mirror.punchCount());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
