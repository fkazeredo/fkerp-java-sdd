package com.fksoft.application.api;

import com.fksoft.application.api.dto.RegisterAdminContractRequest;
import com.fksoft.application.api.dto.RegisterAdminExpenseRequest;
import com.fksoft.application.api.dto.RegisterAdminSupplierRequest;
import com.fksoft.domain.admin.AdminContractView;
import com.fksoft.domain.admin.AdminExpenseView;
import com.fksoft.domain.admin.AdminService;
import com.fksoft.domain.admin.AdminSupplierStatus;
import com.fksoft.domain.admin.AdminSupplierView;
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
 * REST endpoints for the Admin module (SPEC-0025): registering an administrative supplier (201) and
 * its contracts (201), fetching/listing suppliers, registering a recurring expense (201 — which
 * creates the Finance ledger entry and lists the required documents), and triggering the
 * contract-expiry alert sweep (DL-0087). The write endpoints require {@code ROLE_FINANCE} (gated in
 * {@code SecurityConfig}, DL-0088); the delivery layer resolves the acting user for audit. All
 * calls go straight to {@link AdminService}.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;
  private final UserContextProvider userContextProvider;

  // --- Suppliers (BR1) ---

  @PostMapping("/suppliers")
  public ResponseEntity<AdminSupplierView> registerSupplier(
      @Valid @RequestBody RegisterAdminSupplierRequest request) {
    AdminSupplierView view = adminService.registerSupplier(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/suppliers/{id}")
  public AdminSupplierView getSupplier(@PathVariable UUID id) {
    return adminService.getSupplier(id);
  }

  @GetMapping("/suppliers")
  public List<AdminSupplierView> listSuppliers(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) AdminSupplierStatus status) {
    return adminService.listSuppliers(type, status);
  }

  // --- Contracts (BR2) ---

  @PostMapping("/suppliers/{id}/contracts")
  public ResponseEntity<AdminContractView> registerContract(
      @PathVariable UUID id, @Valid @RequestBody RegisterAdminContractRequest request) {
    AdminContractView view = adminService.registerContract(id, request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/suppliers/{id}/contracts")
  public List<AdminContractView> contracts(@PathVariable UUID id) {
    return adminService.contractsForSupplier(id);
  }

  // --- Recurring expenses (BR3) ---

  @PostMapping("/expenses")
  public ResponseEntity<AdminExpenseView> registerExpense(
      @Valid @RequestBody RegisterAdminExpenseRequest request) {
    AdminExpenseView view = adminService.registerExpense(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  // --- Contract-expiry alert sweep (BR5/DL-0087) ---

  @PostMapping("/contracts/flag-expiring")
  public ExpiringSweepResponse flagExpiring() {
    int flagged = adminService.flagExpiringContracts(Instant.now());
    return new ExpiringSweepResponse(flagged);
  }

  /** Result of the contract-expiry sweep: how many contracts were newly flagged. */
  public record ExpiringSweepResponse(int flagged) {}

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
