package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Operational point snapshot aggregate (SPEC-0012): the mirror collected from the vendor portal for
 * a {@code (sourceRef, periodRef)}. It is <strong>operational only</strong> — {@code
 * operationalOnly} is always {@code true} (BR3), an aggregate invariant: this is never the legal
 * AFD/AEJ document (that lives in the Compliance vault). The {@code (sourceRef, periodRef)} pair is
 * unique (BR5 idempotency). Re-collecting a period refreshes the captured payload/marks in place
 * instead of creating a duplicate. Module-internal.
 */
@Entity
@Table(name = "point_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class PointSnapshot {

  @Id private UUID id;

  private String sourceRef;

  private String periodRef;

  private boolean operationalOnly;

  private String payloadRef;

  private int marks;

  private Instant collectedAt;

  private Instant createdAt;

  @Version private Long version;

  /**
   * Collects a new operational snapshot for a source/period (BR3: operational only).
   *
   * @param sourceRef the REP/branch reference
   * @param periodRef the collected period ({@code YYYY-MM})
   * @param payloadRef the opaque reference to the stored mirror
   * @param marks the number of punches captured
   * @param now the collection instant (UTC)
   * @return a new, persistable snapshot, with {@code operationalOnly = true}
   */
  public static PointSnapshot collect(
      String sourceRef, String periodRef, String payloadRef, int marks, Instant now) {
    PointSnapshot snapshot = new PointSnapshot();
    snapshot.id = UUID.randomUUID();
    snapshot.sourceRef = sourceRef;
    snapshot.periodRef = periodRef;
    snapshot.operationalOnly = true; // BR3 — never a legal document, by invariant.
    snapshot.payloadRef = payloadRef;
    snapshot.marks = marks;
    snapshot.collectedAt = now;
    snapshot.createdAt = now;
    return snapshot;
  }

  /**
   * Refreshes an existing snapshot in place on a re-collection of the same {@code (sourceRef,
   * periodRef)} (BR5 idempotency): updates the captured payload/marks and the collection instant,
   * never the identity. {@code operationalOnly} stays {@code true}.
   *
   * @param payloadRef the new opaque reference to the re-captured mirror
   * @param marks the new punch count
   * @param now the new collection instant (UTC)
   */
  public void refresh(String payloadRef, int marks, Instant now) {
    this.payloadRef = payloadRef;
    this.marks = marks;
    this.collectedAt = now;
  }

  /** The snapshot id. */
  public UUID id() {
    return id;
  }

  /** Projects the aggregate to its public read view (without the internal {@code payloadRef}). */
  public PointSnapshotView toView() {
    return new PointSnapshotView(id, sourceRef, periodRef, operationalOnly, marks, collectedAt);
  }
}
