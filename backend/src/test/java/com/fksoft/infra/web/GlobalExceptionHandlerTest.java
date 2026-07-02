package com.fksoft.infra.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fksoft.domain.error.DomainException;
import com.fksoft.infra.i18n.MessageSourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies the presentation-layer error contract (ADR 0011): a {@link DomainException} maps to its
 * configured status (422 by default) with an {@link ApiErrorResponse} body whose {@code code} is
 * the domain code and whose {@code message} falls back to the code when no i18n key exists; an
 * unexpected exception maps to 500 {@code error.internal} without leaking internals. Uses a
 * standalone MockMvc setup so the handler is exercised in isolation (no Spring context).
 */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    GlobalExceptionHandler advice =
        new GlobalExceptionHandler(
            new MessageSourceConfig().messageSource(), new HttpErrorMapping());
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SampleController()).setControllerAdvice(advice).build();
  }

  @Test
  void mapsDomainExceptionToUnprocessableEntityWithStableBody() throws Exception {
    mockMvc
        .perform(get("/__test__/domain-error"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("test.sample.error"))
        .andExpect(jsonPath("$.message").value("test.sample.error"))
        .andExpect(jsonPath("$.fields").isArray());
  }

  @Test
  void mapsUnexpectedExceptionToInternalServerError() throws Exception {
    mockMvc
        .perform(get("/__test__/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("error.internal"));
  }

  @Test
  void mapsOptimisticLockConflictToConflictNotServerError() throws Exception {
    mockMvc
        .perform(get("/__test__/stale"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("error.conflict"));
  }

  /** Minimal controller used only to trigger the handlers under test. */
  @RestController
  static class SampleController {

    @GetMapping("/__test__/domain-error")
    String domainError() {
      throw new SampleDomainException();
    }

    @GetMapping("/__test__/boom")
    String boom() {
      throw new IllegalStateException("boom");
    }

    @GetMapping("/__test__/stale")
    String stale() {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Object.class, "id");
    }
  }

  /** Test-only domain exception (excluded from production scans by DoNotIncludeTests). */
  static class SampleDomainException extends DomainException {
    SampleDomainException() {
      super("test.sample.error");
    }
  }
}
