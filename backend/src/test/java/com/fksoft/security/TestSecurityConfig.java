package com.fksoft.security;

import com.fksoft.infra.security.RestAccessDeniedHandler;
import com.fksoft.infra.security.RestAuthenticationEntryPoint;
import com.fksoft.infra.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Test-only security configuration (SPEC-0024/DL-0081). It keeps the <strong>real</strong> security
 * chain mounted (same {@link SecurityConfig#configure} authorization rules and JWT resource
 * server), so the security layer is exercised, not removed. To keep the 434 pre-existing
 * integration tests green without each one minting a token, it inserts a filter that — <strong>only
 * when no {@code Authorization} header is present</strong> — authenticates the request as a
 * full-access test actor.
 *
 * <p>When a request DOES carry an {@code Authorization} header, this filter steps aside and the
 * real JWT bearer filter processes it — so the new security tests can exercise the genuine 401
 * (invalid token) and 403 (valid token, wrong role) paths by sending tokens minted by the real
 * issuer.
 */
@TestConfiguration
public class TestSecurityConfig {

  /** The full-access actor the existing suite runs as (mirrors the old dev stub's broad roles). */
  private static final List<SimpleGrantedAuthority> FULL_ACCESS =
      List.of(
          new SimpleGrantedAuthority("ROLE_DEV"),
          new SimpleGrantedAuthority("ROLE_DIRECTOR"),
          new SimpleGrantedAuthority("ROLE_FINANCE"),
          new SimpleGrantedAuthority("ROLE_OPERATIONS"),
          new SimpleGrantedAuthority("ROLE_IT"),
          new SimpleGrantedAuthority("ROLE_POLICY_ADMIN"),
          new SimpleGrantedAuthority("ROLE_VIEWER"));

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain testSecurityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    SecurityConfig.configure(
        http, authenticationEntryPoint, accessDeniedHandler, jwtAuthenticationConverter);
    // Before the bearer filter: if there is no Authorization header, authenticate the test actor.
    http.addFilterBefore(new DefaultTestActorFilter(), BasicAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Authenticates the default full-access test actor when the request carries no {@code
   * Authorization} header — leaving token-bearing requests to the real JWT filter (so 401/403 stay
   * testable).
   */
  static final class DefaultTestActorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      boolean hasToken = StringUtils.hasText(request.getHeader("Authorization"));
      if (!hasToken && SecurityContextHolder.getContext().getAuthentication() == null) {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("test-actor", "n/a", FULL_ACCESS);
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
      filterChain.doFilter(request, response);
    }
  }
}
