import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { AccessAuditType, AccessAuditView, RoleView } from './identity.models';
import { IdentityService } from './identity.service';

/**
 * Identity/access screen (SPEC-0024, SPEC-0029 16d): the local role/permission catalogue and the
 * access-audit trail (login/denial). Both reads require DIRECTOR or IT — the backend is the authority,
 * so a caller without either gets a 403 rendered as the permission state by {@link ScreenState}. Login
 * happens at the external OIDC IdP; there is no credential management here.
 */
@Component({
  selector: 'app-identity-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './identity-page.html',
})
export class IdentityPage implements OnInit {
  private readonly identityService = inject(IdentityService);

  readonly auditTypes: AccessAuditType[] = ['AUTH_LOGIN', 'ACCESS_DENIED', 'SECURITY_EVENT'];

  // Role catalogue.
  readonly rolesState = signal<'loading' | 'success' | 'error'>('loading');
  readonly roles = signal<RoleView[]>([]);
  readonly rolesError = signal<string | null>(null);

  readonly rolesListState = computed<ScreenStateKind>(() => {
    const s = this.rolesState();
    if (s === 'success') {
      return this.roles().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Access-audit trail.
  auditActor = '';
  auditType: AccessAuditType | '' = '';
  readonly auditState = signal<'loading' | 'success' | 'error'>('loading');
  readonly audit = signal<AccessAuditView[]>([]);
  readonly auditError = signal<string | null>(null);

  readonly auditListState = computed<ScreenStateKind>(() => {
    const s = this.auditState();
    if (s === 'success') {
      return this.audit().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  ngOnInit(): void {
    this.loadRoles();
    this.loadAudit();
  }

  /** (Re)loads the role/permission catalogue. */
  loadRoles(): void {
    this.rolesState.set('loading');
    this.identityService.roles().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.rolesState.set('success');
      },
      error: (error: ApiError) => {
        this.rolesError.set(error?.code ?? 'error.internal');
        this.rolesState.set('error');
      },
    });
  }

  /** (Re)loads the access-audit trail using the current filters. */
  loadAudit(): void {
    this.auditState.set('loading');
    this.identityService.accessAudit(this.auditActor.trim() || undefined, this.auditType).subscribe({
      next: (page) => {
        this.audit.set(page.content);
        this.auditState.set('success');
      },
      error: (error: ApiError) => {
        this.auditError.set(error?.code ?? 'error.internal');
        this.auditState.set('error');
      },
    });
  }

  /** PrimeNG Tag severity for an access-audit type. */
  auditSeverity(type: string): 'success' | 'danger' | 'warn' {
    if (type === 'AUTH_LOGIN') {
      return 'success';
    }
    return type === 'ACCESS_DENIED' ? 'danger' : 'warn';
  }
}
