package com.fksoft.application.api;

import com.fksoft.application.api.dto.CancellationPolicyRequest;
import com.fksoft.domain.booking.CancellationPolicyAdminService;
import com.fksoft.domain.booking.CancellationPolicyView;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints to administer the cancellation/no-show policy per product/supplier scope
 * (SPEC-0010 API). Authorization is an administrative role (enforced once Identity/SPEC-0024 lands;
 * today the dev {@code UserContextProvider} stands in). The delivery layer resolves the acting user
 * for audit.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class CancellationPolicyAdminController {

  private final CancellationPolicyAdminService policyService;
  private final UserContextProvider userContextProvider;

  @GetMapping("/{ref}/cancellation-policy")
  public CancellationPolicyView get(@PathVariable("ref") String ref) {
    return policyService.get(ref);
  }

  @PutMapping("/{ref}/cancellation-policy")
  public CancellationPolicyView put(
      @PathVariable("ref") String ref, @Valid @RequestBody CancellationPolicyRequest request) {
    return policyService.put(ref, request.toPolicy(), request.toNoShowPolicy(), actor());
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
