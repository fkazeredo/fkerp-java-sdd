package com.fksoft.application.api;

import com.fksoft.application.api.dto.DefineRuleRequest;
import com.fksoft.application.api.dto.IssueDirectiveRequest;
import com.fksoft.application.api.dto.ParameterRuleResponse;
import com.fksoft.application.api.dto.ResolvedParameterResponse;
import com.fksoft.domain.commercialpolicy.CommercialPolicyService;
import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.infra.security.UserContext;
import com.fksoft.infra.security.UserContextProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the CommercialPolicy module (SPEC-0014). The Open-Host {@code /resolve} reads
 * a governed parameter with provenance; {@code /rules} and {@code /directives} define rules in
 * runtime (self-service, audited — DL-0038): rules need an admin/curator role, directives need the
 * director role and a justification. The delivery layer resolves the acting user (audit) and
 * forwards roles for authorization (the backend is the final authority — security.md). Quoting does
 * NOT use this controller; it consumes the engine through the {@code MarkupProvider} port.
 */
@Tag(name = "Commercial Policy", description = "Parâmetros governados e diretivas (precedência)")
@RestController
@RequestMapping("/api/commercial-policy")
@RequiredArgsConstructor
public class CommercialPolicyController {

  private final CommercialPolicyService policyService;
  private final UserContextProvider userContextProvider;

  /**
   * Resolves a parameter for a scope, returning value + provenance (BR2). A key with no
   * SYSTEM_DEFAULT yields {@code 404 policy.parameter.unknown} (BR4).
   */
  @GetMapping("/resolve")
  public ResolvedParameterResponse resolve(
      @RequestParam String key,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) String productRef,
      @RequestParam(required = false) String channel) {
    return ResolvedParameterResponse.from(
        policyService.resolve(
            ParameterKey.parse(key), new ParameterScope(accountId, productRef, channel)));
  }

  /** Defines a POLICY/PROMOTION/CONTRACT rule (admin/curator role; audited). */
  @PostMapping("/rules")
  public ResponseEntity<ParameterRuleResponse> defineRule(
      @Valid @RequestBody DefineRuleRequest request) {
    UserContext user = userContextProvider.currentUser();
    ParameterRuleResponse response =
        ParameterRuleResponse.from(
            policyService.defineRule(request.toCommand(), user.roles(), user.username()));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Issues a director's directive (director role + justification; reinforced audit — BR5). */
  @PostMapping("/directives")
  public ResponseEntity<ParameterRuleResponse> issueDirective(
      @Valid @RequestBody IssueDirectiveRequest request) {
    UserContext user = userContextProvider.currentUser();
    ParameterRuleResponse response =
        ParameterRuleResponse.from(
            policyService.defineRule(request.toCommand(), user.roles(), user.username()));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Lists rules for audit/curation, optionally filtered by key and/or layer. */
  @GetMapping("/rules")
  public List<ParameterRuleResponse> listRules(
      @RequestParam(required = false) String key,
      @RequestParam(required = false) ParameterLayer layer) {
    ParameterKey parsedKey = key != null && !key.isBlank() ? ParameterKey.parse(key) : null;
    return policyService.listRules(parsedKey, layer).stream()
        .map(ParameterRuleResponse::from)
        .toList();
  }
}
