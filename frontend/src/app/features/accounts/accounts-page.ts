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
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { AccountResponse, AccountStatus, LegalType } from './accounts.models';
import { AccountsService } from './accounts.service';

/**
 * Accounts screen (SPEC-0002, repaginated SPEC-0026): a creation form plus a list filtered by
 * status, with the loading/empty/error/permission states (BR8) via {@link ScreenState}. Errors are
 * shown by their stable backend code (e.g. {@code account.document.duplicate}). Implements
 * {@link FormLeaveGuard} so leaving with a half-filled, unsubmitted form asks for confirmation (BR9).
 */
@Component({
  selector: 'app-accounts-page',
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
  templateUrl: './accounts-page.html',
})
export class AccountsPage implements OnInit, FormLeaveGuard {
  private readonly accountsService = inject(AccountsService);

  readonly legalTypes: LegalType[] = ['CNPJ', 'MEI', 'CPF'];
  readonly statuses: AccountStatus[] = ['ACTIVE', 'SUSPENDED', 'INACTIVE'];

  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly accounts = signal<AccountResponse[]>([]);
  readonly errorCode = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  /** The state passed to the shared ScreenState (success collapses to empty when the list is empty). */
  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.accounts().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  filter: AccountStatus | '' = '';
  legalType: LegalType = 'CNPJ';
  documentNumber = '';
  displayName = '';
  cadastur = '';
  iata = '';

  ngOnInit(): void {
    this.load();
  }

  /** Whether the creation form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.submitting() && (!!this.documentNumber || !!this.displayName);
  }

  /** (Re)loads the list using the current status filter. */
  load(): void {
    this.state.set('loading');
    this.accountsService.list(this.filter).subscribe({
      next: (page) => {
        this.accounts.set(page.content);
        this.state.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.state.set('error');
      },
    });
  }

  /** Submits the creation form; on success resets it and reloads the list. */
  submit(): void {
    this.submitting.set(true);
    this.submitError.set(null);
    this.accountsService
      .create({
        legalType: this.legalType,
        documentNumber: this.documentNumber,
        displayName: this.displayName,
        cadastur: this.cadastur || null,
        iata: this.iata || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.documentNumber = '';
          this.displayName = '';
          this.cadastur = '';
          this.iata = '';
          this.load();
        },
        error: (error: ApiError) => {
          this.submitting.set(false);
          this.submitError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** PrimeNG Tag severity for an account status. */
  statusSeverity(status: AccountStatus): 'success' | 'warn' | 'secondary' {
    if (status === 'ACTIVE') {
      return 'success';
    }
    return status === 'SUSPENDED' ? 'warn' : 'secondary';
  }
}
