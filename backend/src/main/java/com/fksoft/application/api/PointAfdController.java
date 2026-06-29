package com.fksoft.application.api;

import com.fksoft.domain.compliance.DocumentType;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.people.PointAfdInvalidException;
import com.fksoft.infra.integration.pointclock.AfdIngestionService;
import com.fksoft.infra.security.UserContextProvider;
import java.io.IOException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestion endpoint for the signed legal time record AFD/AEJ (SPEC-0012; DL-0029). The signed
 * {@code .p7s} from the official export is uploaded (multipart), its signature/integrity is
 * verified, and it is archived in the Compliance vault with 5-year retention. Returns {@code 201
 * Created} with the archived vault document, or {@code 400 point.afd.invalid} when verification
 * fails — a tampered artifact never enters the vault (BR4).
 */
@RestController
@RequestMapping("/api/integration/point")
@RequiredArgsConstructor
public class PointAfdController {

  private final AfdIngestionService afdIngestionService;
  private final UserContextProvider userContextProvider;

  @PostMapping(path = "/afd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentView> ingestAfd(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "type", defaultValue = "TIME_RECORD_AFD") DocumentType type,
      @RequestParam("issuedAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt,
      @RequestParam("periodRef") String periodRef,
      @RequestParam("expectedContentHash") String expectedContentHash) {
    DocumentView document =
        afdIngestionService.ingest(
            type,
            contentOf(file),
            file.getOriginalFilename(),
            expectedContentHash,
            issuedAt,
            periodRef,
            actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(document);
  }

  private static byte[] contentOf(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new PointAfdInvalidException();
    }
    try {
      return file.getBytes();
    } catch (IOException io) {
      throw new PointAfdInvalidException();
    }
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
