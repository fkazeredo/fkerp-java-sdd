package com.fksoft.application.api;

import com.fksoft.application.api.dto.RegisterAssetRequest;
import com.fksoft.application.api.dto.RetireAssetRequest;
import com.fksoft.domain.assets.AssetService;
import com.fksoft.domain.assets.AssetStatus;
import com.fksoft.domain.assets.AssetType;
import com.fksoft.domain.assets.AssetView;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Assets module (SPEC-0021): registering an internal asset (201),
 * fetching/listing assets (with combinable {@code type}/{@code status}/{@code expiringWithinDays}
 * filters), retiring an asset with an audited reason, and triggering the license-expiry alert sweep
 * (DL-0066). All calls go straight to {@link AssetService}; the delivery layer resolves the acting
 * user for audit. Assets never prices a sale — there is no commercial endpoint here (BR5).
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetsController {

  private final AssetService assetService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<AssetView> register(@Valid @RequestBody RegisterAssetRequest request) {
    AssetView view = assetService.register(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/{id}")
  public AssetView get(@PathVariable UUID id) {
    return assetService.get(id);
  }

  @GetMapping
  public List<AssetView> list(
      @RequestParam(required = false) AssetType type,
      @RequestParam(required = false) AssetStatus status,
      @RequestParam(required = false) Integer expiringWithinDays) {
    return assetService.list(type, status, expiringWithinDays);
  }

  @PostMapping("/{id}/retire")
  public AssetView retire(@PathVariable UUID id, @Valid @RequestBody RetireAssetRequest request) {
    return assetService.retire(id, request.reason(), actor());
  }

  // --- License-expiry alert sweep (BR3/DL-0066) ---

  @PostMapping("/flag-expiring")
  public ExpiringSweepResponse flagExpiring() {
    int flagged = assetService.flagExpiringLicenses(Instant.now());
    return new ExpiringSweepResponse(flagged);
  }

  /** Result of the license-expiry sweep: how many licenses were newly flagged. */
  public record ExpiringSweepResponse(int flagged) {}

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
