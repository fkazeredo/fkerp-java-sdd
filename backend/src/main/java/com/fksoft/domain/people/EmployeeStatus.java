package com.fksoft.domain.people;

/**
 * Employment status of a collaborator (SPEC-0022 BR1). A simple status enum: {@code ACTIVE}
 * (working), {@code ON_LEAVE} (afastado) and {@code TERMINATED} (desligado). Exposed in the API
 * with these explicit external values.
 */
public enum EmployeeStatus {
  ACTIVE,
  ON_LEAVE,
  TERMINATED
}
