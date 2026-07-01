import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import { AccessAuditType, AccessAuditView, RoleView } from './identity.models';

/**
 * Feature API service for the Identity/access module (SPEC-0024): the local role/permission catalogue
 * (the source of truth of internal authorization) and the access-audit trail (login/denial), read
 * from the Platform's append-only system_audit. Both endpoints require DIRECTOR or IT (SecurityConfig)
 * — the backend is the authority, so a caller without either gets a 403 rendered as the permission
 * state. Login itself happens at the external OIDC IdP; there is no credential endpoint here.
 */
@Injectable({ providedIn: 'root' })
export class IdentityService {
  private readonly http = inject(HttpClient);

  /** The role/permission catalogue (DIRECTOR/IT). */
  roles(): Observable<RoleView[]> {
    return this.http.get<RoleView[]>(`${API_BASE_URL}/identity/roles`);
  }

  /** The access-audit trail (login/denial), newest first, paginated (DIRECTOR/IT). */
  accessAudit(
    actor?: string,
    type?: AccessAuditType | '',
  ): Observable<PageResponse<AccessAuditView>> {
    let params = new HttpParams();
    if (actor) {
      params = params.set('actor', actor);
    }
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<PageResponse<AccessAuditView>>(`${API_BASE_URL}/identity/access-audit`, {
      params,
    });
  }
}
