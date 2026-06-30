package com.fksoft.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.domain.identity.IdentityService;
import com.fksoft.infra.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns the stable {@link ApiErrorResponse} with {@code 403} when an authenticated user lacks the
 * required role (SPEC-0024 BR2), and <strong>audits the denial</strong> (BR3/DL-0083) via {@link
 * IdentityService#recordAccessDenied} — actor/action/resource only, never a token/secret (BR4).
 * i18n message {@code access.denied}.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;
  private final IdentityService identityService;

  public RestAccessDeniedHandler(
      ObjectMapper objectMapper, MessageSource messageSource, IdentityService identityService) {
    this.objectMapper = objectMapper;
    this.messageSource = messageSource;
    this.identityService = identityService;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String actor = authentication != null ? authentication.getName() : null;
    String action = request.getMethod() + " " + request.getRequestURI();
    identityService.recordAccessDenied(actor, action, request.getRequestURI());

    String code = "access.denied";
    String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(ApiErrorResponse.of(code, message)));
  }
}
