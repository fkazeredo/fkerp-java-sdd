package com.fksoft.application.api;

import com.fksoft.application.api.dto.CommissionPreviewRequest;
import com.fksoft.domain.commissioning.CommissionCalculator;
import com.fksoft.domain.commissioning.CommissionInput;
import com.fksoft.domain.commissioning.CommissionStatement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the commission preview (SPEC-0004): shows the agent the two-sided decomposition
 * (supplier/agent/spread) for a base and two percentages. Stateless.
 */
@Tag(name = "Commissioning", description = "Comissão de duas pontas e spread (preview)")
@RestController
@RequestMapping("/api/commissioning")
@RequiredArgsConstructor
public class CommissioningController {

  private final CommissionCalculator commissionCalculator;

  @PostMapping("/preview")
  public CommissionStatement preview(@Valid @RequestBody CommissionPreviewRequest request) {
    return commissionCalculator.compute(
        new CommissionInput(
            request.commissionableBase(),
            request.supplierCommissionPct(),
            request.agentCommissionPct()));
  }
}
