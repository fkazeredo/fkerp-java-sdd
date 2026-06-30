package com.fksoft.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.infra.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns the stable {@link ApiErrorResponse} with a <strong>generic</strong> {@code 401} when a
 * request is unauthenticated (missing/invalid token) — the message never reveals whether a user
 * exists (SPEC-0024 BR4). i18n message {@code auth.unauthenticated}.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
    this.objectMapper = objectMapper;
    this.messageSource = messageSource;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    String code = "auth.unauthenticated";
    String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(ApiErrorResponse.of(code, message)));
  }
}
