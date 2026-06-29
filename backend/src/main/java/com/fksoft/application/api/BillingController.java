package com.fksoft.application.api;

import com.fksoft.application.api.dto.CancelCommissionInvoiceRequest;
import com.fksoft.application.api.dto.CreateCommissionInvoiceRequest;
import com.fksoft.domain.billing.BillingService;
import com.fksoft.domain.billing.CommissionInvoiceView;
import com.fksoft.infra.integration.nfse.BillingIssuanceService;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Billing module (SPEC-0016): create a draft commission invoice, issue it
 * (computes ISS, signs, transmits to the municipality, archives the document and posts the tax),
 * cancel it, and read it. Issuance/cancellation go through the {@link BillingIssuanceService}
 * orchestrator (infra) which wires the Billing/Compliance facades and the NFS-e ACL; reads/draft go
 * straight to the {@link BillingService} domain facade. The delivery layer resolves the acting user
 * for audit.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

  private final BillingService billingService;
  private final BillingIssuanceService issuanceService;
  private final UserContextProvider userContextProvider;

  @PostMapping("/invoices")
  public ResponseEntity<CommissionInvoiceView> create(
      @Valid @RequestBody CreateCommissionInvoiceRequest request) {
    CommissionInvoiceView view =
        billingService.createDraft(
            request.commissionEntryId(),
            request.base(),
            request.municipality(),
            request.serviceCode(),
            actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/invoices/{id}/issue")
  public CommissionInvoiceView issue(@PathVariable UUID id) {
    return issuanceService.issue(id, actor());
  }

  @PostMapping("/invoices/{id}/cancel")
  public CommissionInvoiceView cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelCommissionInvoiceRequest request) {
    return issuanceService.cancel(id, request.reason(), actor());
  }

  @GetMapping("/invoices/{id}")
  public CommissionInvoiceView getById(@PathVariable UUID id) {
    return billingService.getById(id);
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
