package com.fksoft.application.api;

import com.fksoft.application.api.dto.CreateCadastroItemRequest;
import com.fksoft.application.api.dto.UpdateCadastroItemRequest;
import com.fksoft.domain.cadastro.CadastroItemView;
import com.fksoft.domain.cadastro.CadastroService;
import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.infra.security.UserContextProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Cadastro module (SPEC-0031; ADR-0019): the "Cadastros" admin screen backs
 * onto these. Lists the convertible types and the items of a type (reads — authenticated); creates,
 * updates and deactivates items (writes — require {@code ROLE_POLICY_ADMIN}, gated in {@code
 * SecurityConfig} / DL-0115; the delivery layer resolves the acting user for audit). The converted
 * fields elsewhere keep their JSON schema (string) — only these {@code /api/cadastro/*} endpoints
 * are new.
 */
@Tag(name = "Cadastro", description = "Dados de referência editáveis (cadastros)")
@RestController
@RequestMapping("/api/cadastro")
@RequiredArgsConstructor
public class CadastroController {

  private final CadastroService cadastroService;
  private final UserContextProvider userContextProvider;

  @GetMapping("/types")
  public List<CadastroType> types() {
    return cadastroService.listTypes();
  }

  @GetMapping("/items")
  public List<CadastroItemView> items(@RequestParam CadastroType type) {
    return cadastroService.listItems(type);
  }

  @PostMapping("/items")
  public ResponseEntity<CadastroItemView> create(
      @Valid @RequestBody CreateCadastroItemRequest request) {
    CadastroItemView view = cadastroService.create(request.toCommand(), actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PutMapping("/items/{id}")
  public CadastroItemView update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCadastroItemRequest request) {
    return cadastroService.update(id, request.toCommand(), actor());
  }

  @DeleteMapping("/items/{id}")
  public CadastroItemView deactivate(@PathVariable UUID id) {
    return cadastroService.deactivate(id, actor());
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
