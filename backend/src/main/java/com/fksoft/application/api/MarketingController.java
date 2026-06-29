package com.fksoft.application.api;

import com.fksoft.application.api.dto.ConsentStateResponse;
import com.fksoft.application.api.dto.CreateCampaignRequest;
import com.fksoft.application.api.dto.DefineSegmentRequest;
import com.fksoft.application.api.dto.GrantConsentRequest;
import com.fksoft.application.api.dto.SegmentPreviewResponse;
import com.fksoft.domain.marketing.CampaignSendResult;
import com.fksoft.domain.marketing.CampaignView;
import com.fksoft.domain.marketing.ConsentPurpose;
import com.fksoft.domain.marketing.ConsentView;
import com.fksoft.domain.marketing.MarketingService;
import com.fksoft.domain.marketing.SegmentView;
import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.domain.marketing.SubjectType;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Marketing module (SPEC-0019) — slice 8f-1: the consent endpoints. Granting
 * appends a GRANTED row (201), revoking by id appends a REVOKED row (200), and the GET returns the
 * current state plus the append-only history (DL-0056). All calls go straight to the {@link
 * MarketingService} domain facade; the delivery layer resolves the acting user for audit.
 *
 * <p>Slice 8f-2 adds the segment and campaign endpoints: define a segment (validated criteria),
 * preview its reach, create a campaign, and dispatch it — the dispatch filters by consent (BR2) and
 * is idempotent per recipient (BR4). The attribution/erasure endpoints are added in slice 8f-3.
 */
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

  private final MarketingService marketingService;
  private final UserContextProvider userContextProvider;

  @PostMapping("/consents")
  public ResponseEntity<ConsentView> grant(@Valid @RequestBody GrantConsentRequest request) {
    ConsentView view = marketingService.grantConsent(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @DeleteMapping("/consents/{id}")
  public ConsentView revoke(@PathVariable UUID id) {
    return marketingService.revokeConsent(id, actor());
  }

  @GetMapping("/consents")
  public ConsentStateResponse state(
      @RequestParam String subject,
      @RequestParam SubjectType subjectType,
      @RequestParam(defaultValue = "NEWSLETTER") ConsentPurpose purpose) {
    SubjectRef ref = new SubjectRef(subject, subjectType);
    return new ConsentStateResponse(
        marketingService.currentState(ref, purpose), marketingService.history(ref, purpose));
  }

  // --- Segments (BR3) ---

  @PostMapping("/segments")
  public ResponseEntity<SegmentView> defineSegment(
      @Valid @RequestBody DefineSegmentRequest request) {
    SegmentView view = marketingService.defineSegment(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/segments/{id}/preview")
  public SegmentPreviewResponse previewSegment(@PathVariable UUID id) {
    return new SegmentPreviewResponse(id, marketingService.previewSegment(id));
  }

  // --- Campaigns (BR2/BR4) ---

  @PostMapping("/campaigns")
  public ResponseEntity<CampaignView> createCampaign(
      @Valid @RequestBody CreateCampaignRequest request) {
    CampaignView view = marketingService.createCampaign(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/campaigns/{id}")
  public CampaignView getCampaign(@PathVariable UUID id) {
    return marketingService.getCampaign(id);
  }

  @PostMapping("/campaigns/{id}/send")
  public CampaignSendResult sendCampaign(@PathVariable UUID id) {
    return marketingService.sendCampaign(id, actor());
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
