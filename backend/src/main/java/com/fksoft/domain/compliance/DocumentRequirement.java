package com.fksoft.domain.compliance;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Policy row (SPEC-0008 BR4; DL-0012): for a given entry type, which document type is mandatory and
 * in which phase. It is system data, seeded by Flyway and read-only at runtime. The entry type is a
 * value (the Finance {@code EntryType} name), keeping the modules decoupled. Module-internal.
 */
@Entity
@Table(name = "document_requirements")
@IdClass(DocumentRequirement.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class DocumentRequirement {

  @Id private String entryType;

  @Id private String requiredDocumentType;

  @Id
  @Enumerated(EnumType.STRING)
  private RequirementPhase phase;

  /** Composite primary key of {@link DocumentRequirement}. */
  @NoArgsConstructor
  @Getter
  public static class Key implements Serializable {

    private String entryType;
    private String requiredDocumentType;
    private RequirementPhase phase;

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Key key)) {
        return false;
      }
      return java.util.Objects.equals(entryType, key.entryType)
          && java.util.Objects.equals(requiredDocumentType, key.requiredDocumentType)
          && phase == key.phase;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(entryType, requiredDocumentType, phase);
    }
  }
}
