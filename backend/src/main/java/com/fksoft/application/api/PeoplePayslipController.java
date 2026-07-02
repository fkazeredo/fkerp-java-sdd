package com.fksoft.application.api;

import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.people.EmployeeInvalidException;
import com.fksoft.infra.integration.payroll.PayslipArchivingService;
import com.fksoft.infra.security.UserContextProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Archiving endpoint for a collaborator processed payslip/espelho (SPEC-0022 BR5; DL-0072). The
 * file is uploaded (multipart) and archived in the Compliance vault as a {@code PAYROLL} document
 * (5-year retention, personal data) by the {@link PayslipArchivingService} orchestrator. Returns
 * {@code 201 Created} with the archived vault document. People keeps only the {@code documentId} by
 * value — it never becomes a vault (BR6).
 */
@Tag(name = "People Payslip", description = "Arquivamento de holerite no cofre")
@RestController
@RequestMapping("/api/people/employees")
@RequiredArgsConstructor
public class PeoplePayslipController {

  private final PayslipArchivingService payslipArchivingService;
  private final UserContextProvider userContextProvider;

  @PostMapping(path = "/{id}/payslip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentView> archivePayslip(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @RequestParam("period") String period,
      @RequestParam("issuedAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt) {
    DocumentView document =
        payslipArchivingService.archive(
            id,
            contentOf(file),
            file.getOriginalFilename(),
            file.getContentType(),
            issuedAt,
            period,
            actor());
    return ResponseEntity.status(HttpStatus.CREATED).body(document);
  }

  private static byte[] contentOf(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new EmployeeInvalidException();
    }
    try {
      return file.getBytes();
    } catch (IOException io) {
      throw new EmployeeInvalidException();
    }
  }

  private String actor() {
    return userContextProvider.currentUser().username();
  }
}
