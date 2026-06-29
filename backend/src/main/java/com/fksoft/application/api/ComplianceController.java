package com.fksoft.application.api;

import com.fksoft.application.api.dto.AttachDocumentRequest;
import com.fksoft.domain.compliance.CloseCheckView;
import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.ComplianceUploadInvalidException;
import com.fksoft.domain.compliance.DocumentType;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.compliance.SignedFormat;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for the Compliance module (SPEC-0008): upload a document (multipart) with optional
 * link, attach it to an entry, fetch metadata/content, run the period close-check, and purge
 * (guarded by retention). The delivery layer resolves the acting user for audit.
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceController {

  private final ComplianceService complianceService;
  private final UserContextProvider userContextProvider;

  @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentView> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam("type") DocumentType type,
      @RequestParam("issuedAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt,
      @RequestParam(value = "signedFormat", required = false) SignedFormat signedFormat,
      @RequestParam(value = "hasPersonalData", defaultValue = "false") boolean hasPersonalData,
      @RequestParam(value = "entryId", required = false) UUID entryId,
      @RequestParam(value = "entryType", required = false) String entryType) {
    DocumentView view =
        complianceService.upload(
            type,
            contentOf(file),
            file.getOriginalFilename(),
            file.getContentType(),
            issuedAt,
            signedFormat,
            hasPersonalData,
            entryId,
            entryType,
            actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping("/documents/{id}/attach")
  public ResponseEntity<Void> attach(
      @PathVariable UUID id, @Valid @RequestBody AttachDocumentRequest request) {
    complianceService.attach(id, request.entryId(), request.entryType(), actor());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/documents/{id}")
  public DocumentView get(@PathVariable UUID id) {
    return complianceService.getById(id);
  }

  @GetMapping("/documents/{id}/content")
  public ResponseEntity<byte[]> content(@PathVariable UUID id) {
    byte[] content = complianceService.readContent(id, actor());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(content);
  }

  @GetMapping("/close-check")
  public CloseCheckView closeCheck(@RequestParam("period") String period) {
    return complianceService.closeCheck(period);
  }

  @DeleteMapping("/documents/{id}")
  public ResponseEntity<Void> purge(@PathVariable UUID id) {
    complianceService.purge(id, actor());
    return ResponseEntity.noContent().build();
  }

  private static byte[] contentOf(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ComplianceUploadInvalidException();
    }
    try {
      return file.getBytes();
    } catch (IOException io) {
      throw new ComplianceUploadInvalidException();
    }
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
