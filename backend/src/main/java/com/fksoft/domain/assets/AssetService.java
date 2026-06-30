package com.fksoft.domain.assets;

import com.fksoft.domain.assets.internal.Asset;
import com.fksoft.domain.assets.internal.AssetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Assets module (SPEC-0021): registers internal patrimony (equipment,
 * software licenses, other goods), retires it with audit, lists/filters it, and runs the
 * controlled-clock license-expiry alert.
 *
 * <p>Assets is a <strong>leaf</strong> context (DL-0067): it publishes {@link AssetRegistered} and
 * {@link AssetLicenseExpiring} in-process but never calls another module's facade nor consumes
 * their events to post a cost (lancing a patrimony cost is a business rule the spec does not
 * define). It references the Compliance document and the Finance entry by value (no FK — BR2).
 * Asset data is not personal data, so no masking is needed; business events are logged with the
 * assetId and the correlation id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

  private final AssetRepository assetRepository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /** The default look-ahead horizon (days) for the license-expiry alert (DL-0066). */
  @Value("${assets.license.horizon-days:30}")
  private int licenseHorizonDays;

  /**
   * Registers an internal asset (BR1), starting it ACTIVE, and publishes {@link AssetRegistered}. A
   * {@link AssetType#SOFTWARE_LICENSE} must carry an {@code expiresAt} (BR1).
   *
   * @param command the asset details
   * @param actor who registers it (audit)
   * @return the persisted asset view
   * @throws AssetInvalidException when a mandatory field is missing (BR1)
   * @throws LicenseExpiryRequiredException when a license has no expiresAt (BR1)
   */
  @Transactional
  public AssetView register(RegisterAssetCommand command, String actor) {
    if (command == null) {
      throw new AssetInvalidException();
    }
    Instant now = clock.instant();
    Asset asset =
        Asset.register(
            command.type(),
            command.identifier(),
            command.acquisitionDate(),
            command.acquisitionCost(),
            command.expiresAt(),
            command.supplierRef(),
            command.documentId(),
            command.financeEntryId(),
            now,
            actor);
    assetRepository.save(asset);
    events.publishEvent(new AssetRegistered(asset.id(), asset.type(), now));
    log.info(
        "AssetRegistered assetId={} type={} documentId={} financeEntryId={} by={}",
        asset.id(),
        asset.type(),
        command.documentId(),
        command.financeEntryId(),
        actor);
    return asset.toView();
  }

  /**
   * Fetches an asset by id.
   *
   * @throws AssetNotFoundException when no asset has that id
   */
  @Transactional(readOnly = true)
  public AssetView get(UUID id) {
    return assetRepository.findById(id).map(Asset::toView).orElseThrow(AssetNotFoundException::new);
  }

  /**
   * Lists assets, optionally filtered by type and/or status, newest first; when {@code
   * expiringWithinDays} is set, restricts to active software licenses expiring within that window
   * (DL-0066). The filters are combinable.
   *
   * @param type the type filter, or {@code null}
   * @param status the status filter, or {@code null}
   * @param expiringWithinDays the license-expiry window (days), or {@code null}
   * @return the matching asset views
   */
  @Transactional(readOnly = true)
  public List<AssetView> list(AssetType type, AssetStatus status, Integer expiringWithinDays) {
    if (expiringWithinDays != null) {
      LocalDate threshold = LocalDate.now(clock).plusDays(expiringWithinDays);
      return assetRepository.findExpiringWithin(threshold).stream()
          .filter(a -> type == null || a.type() == type)
          .filter(a -> status == null || a.status() == status)
          .map(Asset::toView)
          .toList();
    }
    List<Asset> assets;
    if (type != null && status != null) {
      assets = assetRepository.findByTypeAndStatusOrderByCreatedAtDesc(type, status);
    } else if (type != null) {
      assets = assetRepository.findByTypeOrderByCreatedAtDesc(type);
    } else if (status != null) {
      assets = assetRepository.findByStatusOrderByCreatedAtDesc(status);
    } else {
      assets = assetRepository.findAllByOrderByCreatedAtDesc();
    }
    return assets.stream().map(Asset::toView).toList();
  }

  /**
   * Retires an asset (BR4), recording who/when/why. {@link AssetStatus#RETIRED} is terminal
   * (DL-0068).
   *
   * @param id the asset id
   * @param reason the retirement reason (audited)
   * @param actor who performs it (audit)
   * @return the updated asset view
   * @throws AssetNotFoundException when no asset has that id
   * @throws AssetAlreadyRetiredException when the asset is already RETIRED
   */
  @Transactional
  public AssetView retire(UUID id, String reason, String actor) {
    Asset asset = assetRepository.findById(id).orElseThrow(AssetNotFoundException::new);
    asset.retire(reason, clock.instant(), actor);
    assetRepository.save(asset);
    log.info("AssetRetired assetId={} reason={} by={}", asset.id(), reason, actor);
    return asset.toView();
  }

  /**
   * Sweeps active software licenses that are expiring and raises {@link AssetLicenseExpiring} once
   * each (BR3/DL-0066). The evaluation instant is a parameter (controlled clock, like {@code
   * PortfolioService.flagExpiringContracts} and {@code BookingService.expirePendingBookings}), so
   * the rule is deterministically testable. A license is "expiring" when its {@code expiresAt} is
   * within {@link #licenseHorizonDays} days of {@code now} (or already past). Non-blocking;
   * idempotent per license.
   *
   * @param now the evaluation instant (UTC)
   * @return how many licenses were newly flagged as expiring
   */
  @Transactional
  public int flagExpiringLicenses(Instant now) {
    LocalDate asOf = now.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate threshold = asOf.plusDays(licenseHorizonDays);
    List<Asset> candidates = assetRepository.findExpiringLicenseCandidates(threshold);
    int flagged = 0;
    for (Asset asset : candidates) {
      if (asset.signalExpiringIfDue(now, asOf, licenseHorizonDays)) {
        assetRepository.save(asset);
        events.publishEvent(new AssetLicenseExpiring(asset.id(), asset.expiresAt(), now));
        log.info(
            "AssetLicenseExpiring assetId={} expiresAt={} detectedAt={}",
            asset.id(),
            asset.expiresAt(),
            now);
        flagged++;
      }
    }
    return flagged;
  }
}
