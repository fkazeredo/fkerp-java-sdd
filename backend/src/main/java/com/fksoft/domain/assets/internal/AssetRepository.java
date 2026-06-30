package com.fksoft.domain.assets.internal;

import com.fksoft.domain.assets.AssetStatus;
import com.fksoft.domain.assets.AssetType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Command/query repository for the {@link Asset} aggregate (SPEC-0021). Module-internal: other
 * modules never touch it (Spring Modulith).
 */
public interface AssetRepository extends JpaRepository<Asset, UUID> {

  /** All assets, newest first. */
  List<Asset> findAllByOrderByCreatedAtDesc();

  /** All assets of a type, newest first. */
  List<Asset> findByTypeOrderByCreatedAtDesc(AssetType type);

  /** All assets in a status, newest first. */
  List<Asset> findByStatusOrderByCreatedAtDesc(AssetStatus status);

  /** All assets of a type and status, newest first. */
  List<Asset> findByTypeAndStatusOrderByCreatedAtDesc(AssetType type, AssetStatus status);

  /**
   * Active software licenses not yet signaled whose {@code expiresAt} is on/before the threshold —
   * the candidates for the expiry alert sweep (DL-0066).
   *
   * @param threshold the inclusive upper bound on {@code expiresAt}
   * @return the candidate licenses
   */
  @Query(
      "SELECT a FROM Asset a WHERE a.type = com.fksoft.domain.assets.AssetType.SOFTWARE_LICENSE "
          + "AND a.status = com.fksoft.domain.assets.AssetStatus.ACTIVE "
          + "AND a.expirySignaledAt IS NULL AND a.expiresAt IS NOT NULL "
          + "AND a.expiresAt <= :threshold")
  List<Asset> findExpiringLicenseCandidates(@Param("threshold") LocalDate threshold);

  /**
   * Active software licenses with an {@code expiresAt} on/before the threshold — the ad-hoc {@code
   * ?expiringWithinDays} listing (DL-0066), independent of the alert's signaled state.
   *
   * @param threshold the inclusive upper bound on {@code expiresAt}
   * @return the licenses expiring within the window, newest expiry first
   */
  @Query(
      "SELECT a FROM Asset a WHERE a.type = com.fksoft.domain.assets.AssetType.SOFTWARE_LICENSE "
          + "AND a.status = com.fksoft.domain.assets.AssetStatus.ACTIVE "
          + "AND a.expiresAt IS NOT NULL AND a.expiresAt <= :threshold "
          + "ORDER BY a.expiresAt ASC")
  List<Asset> findExpiringWithin(@Param("threshold") LocalDate threshold);
}
