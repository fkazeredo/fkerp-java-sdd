/** Read view of a role in the local role/permission catalogue (SPEC-0024 BR16). */
export interface RoleView {
  name: string;
  description: string;
  permissions: string[];
}

/**
 * Access-audit entry types (SPEC-0024 — login/denial). Mirrors the backend {@code AuditType}
 * subset the access-audit trail surfaces (union of AUTH_LOGIN + ACCESS_DENIED + SECURITY_EVENT when
 * no type filter is set).
 */
export type AccessAuditType = 'AUTH_LOGIN' | 'ACCESS_DENIED' | 'SECURITY_EVENT';

/** Read view of an access-audit entry (SPEC-0024 — metadata only). */
export interface AccessAuditView {
  id: string;
  occurredAt: string;
  actor: string;
  type: string;
  detail: string;
  correlationId: string;
}
