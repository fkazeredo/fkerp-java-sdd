package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Round-trip tests of the Authorization Server's JDBC state (Fase 19g, DL-0129/ADR-0020) against
 * the real Postgres schema (V39). The HA property under test: a client registration and an
 * in-flight authorization saved by one instance are readable by another (here: a second repository/
 * service instance over the same database) — so any instance can complete a flow a peer started,
 * and a restart loses nothing.
 */
class JdbcAuthorizationStateIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM oauth2_authorization");
    jdbcTemplate.execute("DELETE FROM oauth2_registered_client");
  }

  @Test
  void aClientSavedByOneInstanceIsReadByAnother() {
    JdbcRegisteredClientRepository instanceA = new JdbcRegisteredClientRepository(jdbcTemplate);
    instanceA.save(spaClient("client-a"));

    JdbcRegisteredClientRepository instanceB = new JdbcRegisteredClientRepository(jdbcTemplate);
    RegisteredClient found = instanceB.findByClientId("client-a");

    assertThat(found).isNotNull();
    assertThat(found.getClientAuthenticationMethods()).contains(ClientAuthenticationMethod.NONE);
    assertThat(found.getClientSettings().isRequireProofKey()).isTrue();
  }

  @Test
  void anInFlightAuthorizationSavedByOneInstanceIsReadByAnother() {
    JdbcRegisteredClientRepository clients = new JdbcRegisteredClientRepository(jdbcTemplate);
    RegisteredClient client = spaClient("client-b");
    clients.save(client);

    JdbcOAuth2AuthorizationService instanceA =
        new JdbcOAuth2AuthorizationService(jdbcTemplate, clients);
    OAuth2Authorization authorization =
        OAuth2Authorization.withRegisteredClient(client)
            .principalName("ops")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .attribute("state", "xyz")
            .build();
    instanceA.save(authorization);

    JdbcOAuth2AuthorizationService instanceB =
        new JdbcOAuth2AuthorizationService(jdbcTemplate, clients);
    OAuth2Authorization found = instanceB.findById(authorization.getId());

    assertThat(found).isNotNull();
    assertThat(found.getPrincipalName()).isEqualTo("ops");
    assertThat(found.<String>getAttribute("state")).isEqualTo("xyz");
  }

  private static RegisteredClient spaClient(String clientId) {
    return RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(clientId)
        .clientName("test client")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost:4200")
        .scope("openid")
        .clientSettings(ClientSettings.builder().requireProofKey(true).build())
        .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build())
        .build();
  }
}
