package com.fksoft.application.api;

import com.fksoft.application.api.dto.RegisterBrandRequest;
import com.fksoft.application.api.dto.RegisterContractRequest;
import com.fksoft.domain.portfolio.BrandStatus;
import com.fksoft.domain.portfolio.BrandView;
import com.fksoft.domain.portfolio.ContractCoverage;
import com.fksoft.domain.portfolio.ContractView;
import com.fksoft.domain.portfolio.PortfolioService;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
 * REST endpoints for the Portfolio module (SPEC-0020) — slice 8g-1: brands and representation
 * contracts. Registering a brand (201), listing/fetching brands, deactivating a brand, registering
 * a contract (201), checking contract coverage (a read-model alert, never a block — DL-0061) and
 * triggering the expiry alert sweep (DL-0063). All calls go straight to {@link PortfolioService};
 * the delivery layer resolves the acting user for audit.
 *
 * <p>Slice 8g-2 adds the goals and the realized-vs-goal endpoints.
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

  private final PortfolioService portfolioService;
  private final UserContextProvider userContextProvider;

  // --- Brands (BR1) ---

  @PostMapping("/brands")
  public ResponseEntity<BrandView> registerBrand(@Valid @RequestBody RegisterBrandRequest request) {
    BrandView view = portfolioService.registerBrand(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/brands/{id}")
  public BrandView getBrand(@PathVariable UUID id) {
    return portfolioService.getBrand(id);
  }

  @GetMapping("/brands")
  public List<BrandView> listBrands(@RequestParam(required = false) BrandStatus status) {
    return portfolioService.listBrands(status);
  }

  @DeleteMapping("/brands/{id}")
  public BrandView deactivateBrand(@PathVariable UUID id) {
    return portfolioService.deactivateBrand(id, actor());
  }

  // --- Representation contracts (BR2) ---

  @PostMapping("/brands/{brandRef}/contracts")
  public ResponseEntity<ContractView> registerContract(
      @PathVariable String brandRef, @Valid @RequestBody RegisterContractRequest request) {
    ContractView view =
        portfolioService.registerContract(brandRef, request.toCommand(brandRef), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/brands/{brandRef}/contracts")
  public List<ContractView> contracts(@PathVariable String brandRef) {
    return portfolioService.contractsForBrand(brandRef);
  }

  @GetMapping("/brands/{brandRef}/contract-coverage")
  public ContractCoverage contractCoverage(
      @PathVariable String brandRef,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
    return portfolioService.contractCoverage(brandRef, on);
  }

  // --- Expiry alert sweep (BR5/DL-0063) ---

  @PostMapping("/contracts/flag-expiring")
  public ExpiringSweepResponse flagExpiring() {
    int flagged = portfolioService.flagExpiringContracts(java.time.Instant.now());
    return new ExpiringSweepResponse(flagged);
  }

  /** Result of the expiry sweep: how many contracts were newly flagged. */
  public record ExpiringSweepResponse(int flagged) {}

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
