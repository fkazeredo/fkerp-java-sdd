package com.fksoft.pointclock;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.compliance.DocumentType;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.compliance.SignedFormat;
import com.fksoft.infra.integration.pointclock.AfdEnvelopeFixtures;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * End-to-end tests for the AFD/AEJ legal ingestion (SPEC-0012 BR4 slice 11c) against real Postgres.
 * Proves the legal path is distinct from the operational snapshot: a valid signed {@code .p7s} is
 * archived in the Compliance vault as a {@code Document} with {@code signedFormat=CAdES_P7S} and
 * <strong>5-year retention</strong> (reusing SPEC-0008); a tampered/invalid artifact is rejected
 * with {@code 400 point.afd.invalid} and nothing is stored.
 */
class AfdIngestionIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final byte[] AFD_CONTENT =
      ("00000000012026063003100000ACME TRAVEL LTDA AFD NSR ORDERED RECORDS CPF PIS")
          .getBytes(StandardCharsets.UTF_8);

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
  }

  @Test
  void validSignedAfdIsArchivedInTheVaultWithFiveYearRetention() {
    byte[] envelope = AfdEnvelopeFixtures.signedEnvelope(AFD_CONTENT);
    String expectedHash = AfdEnvelopeFixtures.sha256(AFD_CONTENT);

    ResponseEntity<DocumentView> response =
        post(
            envelope, "TIME_RECORD_AFD", "2026-06-30", "2026-06", expectedHash, DocumentView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DocumentView document = response.getBody();
    assertThat(document).isNotNull();
    assertThat(document.type()).isEqualTo(DocumentType.TIME_RECORD_AFD);
    assertThat(document.signedFormat()).isEqualTo(SignedFormat.CAdES_P7S);
    assertThat(document.hasPersonalData()).isTrue(); // CPF/PIS (LGPD)
    // 5-year legal retention (CTN 173/174; trabalhista) — issued 2026-06-30 → 2031-06-30.
    assertThat(document.retentionUntil()).isEqualTo(LocalDate.of(2031, 6, 30));

    Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class);
    assertThat(stored).isEqualTo(1);
  }

  @Test
  void aejIsArchivedToo() {
    byte[] envelope = AfdEnvelopeFixtures.signedEnvelope(AFD_CONTENT);
    String expectedHash = AfdEnvelopeFixtures.sha256(AFD_CONTENT);

    ResponseEntity<DocumentView> response =
        post(
            envelope,
            "PROCESSED_JOURNAL_AEJ",
            "2026-06-30",
            "2026-06",
            expectedHash,
            DocumentView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().type()).isEqualTo(DocumentType.PROCESSED_JOURNAL_AEJ);
    assertThat(response.getBody().signedFormat()).isEqualTo(SignedFormat.CAdES_P7S);
  }

  @Test
  void tamperedAfdIsRejectedWith400AndNothingIsStored() {
    byte[] tampered =
        AfdEnvelopeFixtures.signedEnvelope("TAMPERED".getBytes(StandardCharsets.UTF_8));
    // Declared hash is of the ORIGINAL content — the file was tampered.
    String expectedHashOfOriginal = AfdEnvelopeFixtures.sha256(AFD_CONTENT);

    ResponseEntity<ApiErrorResponse> response =
        post(
            tampered,
            "TIME_RECORD_AFD",
            "2026-06-30",
            "2026-06",
            expectedHashOfOriginal,
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("point.afd.invalid");
    Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class);
    assertThat(stored).isZero();
  }

  @Test
  void aFileThatIsNotASignedEnvelopeIsRejectedWith400() {
    byte[] notSigned = AfdEnvelopeFixtures.notAnEnvelope();

    ResponseEntity<ApiErrorResponse> response =
        post(
            notSigned,
            "TIME_RECORD_AFD",
            "2026-06-30",
            "2026-06",
            AfdEnvelopeFixtures.sha256(notSigned),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("point.afd.invalid");
    Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class);
    assertThat(stored).isZero();
  }

  private <T> ResponseEntity<T> post(
      byte[] file,
      String type,
      String issuedAt,
      String periodRef,
      String expectedContentHash,
      Class<T> responseType) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart(file));
    body.add("type", type);
    body.add("issuedAt", issuedAt);
    body.add("periodRef", periodRef);
    body.add("expectedContentHash", expectedContentHash);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return restTemplate.postForEntity(
        "/api/integration/point/afd", new HttpEntity<>(body, headers), responseType);
  }

  private static ByteArrayResource filePart(byte[] content) {
    return new ByteArrayResource(content) {
      @Override
      public String getFilename() {
        return "afd.p7s";
      }
    };
  }
}
