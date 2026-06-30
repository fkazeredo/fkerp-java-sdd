package com.fksoft.infra.security;

import com.fksoft.domain.identity.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints the in-house access token (SPEC-0024/DL-0079): an HS256 JWT carrying the user id ({@code
 * uid}), username ({@code preferred_username}) and roles ({@code roles}) of an {@link
 * AuthenticatedUser}. The ERP is the Resource Server of this issuer; the live external OIDC IdP is
 * Phase 13 (which would replace this issuer and the verifier — the {@code UserContextProvider} port
 * stays the same).
 *
 * <p>The signing secret lives in configuration per environment, never in code or logs (BR4).
 */
@Component
public class JwtIssuer {

  private final JwtEncoder jwtEncoder;
  private final Clock clock;
  private final long ttlSeconds;
  private final String issuer;

  public JwtIssuer(JwtEncoder jwtEncoder, Clock clock, SecurityProperties properties) {
    this.jwtEncoder = jwtEncoder;
    this.clock = clock;
    this.ttlSeconds = properties.getJwt().getTtlSeconds();
    this.issuer = properties.getJwt().getIssuer();
  }

  /** The configured token lifetime, in seconds (echoed to the client as {@code expiresIn}). */
  public long ttlSeconds() {
    return ttlSeconds;
  }

  /** Issues a signed JWT for the authenticated user. */
  public String issue(AuthenticatedUser user) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(now.plus(ttlSeconds, ChronoUnit.SECONDS))
            .subject(user.userId().toString())
            .claim(SecurityPrincipals.USER_ID_CLAIM, user.userId().toString())
            .claim(SecurityPrincipals.USERNAME_CLAIM, user.username())
            .claim(SecurityPrincipals.ROLES_CLAIM, List.copyOf(user.roles()))
            .build();
    JwsHeader header =
        JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
