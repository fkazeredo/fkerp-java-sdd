package com.fksoft.domain.compliance;

import java.util.List;

/**
 * Public cross-module read port of the Compliance module (SPEC-0025; DL-0086): lets another module
 * (Admin) learn which document types a Finance entry type requires <em>at registration</em>, so it
 * can tell the operator what to attach for the month to close. It is a <strong>read</strong> — the
 * caller references the requirement, it never imposes it (the veto stays Finance+Compliance, BR4).
 * The entry type crosses the boundary as a value ({@code EntryType.name()}), keeping the modules
 * decoupled.
 */
public interface DocumentRequirementDirectory {

  /**
   * The document types required at registration for the given entry type (DL-0012). Returns an
   * empty list when the type has no mandatory document at registration.
   *
   * @param entryType the Finance entry type name (value)
   * @return the required document type names (empty when none)
   */
  List<String> requiredAtRegistration(String entryType);
}
