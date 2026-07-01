/** Lifecycle status of a collaborator (SPEC-0022). */
export type EmployeeStatus = 'ACTIVE' | 'ON_LEAVE' | 'TERMINATED';

/** Treatment status of a journey discrepancy (SPEC-0022 BR4). */
export type DiscrepancyStatus = 'OPEN' | 'RESOLVED';

/** Kind of a journey discrepancy (SPEC-0022 BR4). */
export type DiscrepancyKind = string;

/** Read view of a collaborator (SPEC-0022). */
export interface EmployeeView {
  id: string;
  identifier: string;
  admissionDate: string;
  contractedJourney: string;
  status: EmployeeStatus;
  contractDocumentId: string | null;
}

/** Read view of a processed period journey (SPEC-0022 BR2). */
export interface JourneyView {
  employeeId: string;
  period: string;
  workedHours: string;
  contractedHours: string;
  balance: string;
  snapshotRef: string | null;
  processedAt: string;
}

/** Read view of the time-bank for a collaborator/period (SPEC-0022 BR3). */
export interface TimeBankView {
  period: string;
  workedHours: string;
  contractedHours: string;
  balance: string;
  discrepancies: number;
}

/** Read view of a journey discrepancy in the treatment queue (SPEC-0022 BR4). */
export interface DiscrepancyView {
  id: string;
  employeeId: string;
  period: string;
  kind: DiscrepancyKind;
  status: DiscrepancyStatus;
  detail: string;
  createdAt: string;
}

/** Body for `POST /api/people/employees` (SPEC-0022 BR1). */
export interface CreateEmployeeRequest {
  identifier: string;
  admissionDate: string;
  contractedJourney: string;
  contractDocumentId?: string | null;
}

/** Body for `POST /api/people/employees/{id}/journey` (SPEC-0022 BR2). */
export interface ProcessJourneyRequest {
  period: string;
  sourceRef: string;
  workedMinutes: number;
  workingDays: number;
  expectedPunches: number;
  actualPunches: number;
}
