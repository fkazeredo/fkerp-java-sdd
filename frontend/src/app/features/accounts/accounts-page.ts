import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiError } from '../../core/http/api-error';
import { AccountResponse, AccountStatus, LegalType } from './accounts.models';
import { AccountsService } from './accounts.service';

type ViewState = 'loading' | 'success' | 'error';

/**
 * Accounts screen (SPEC-0002): a creation form plus a list filtered by status, with the
 * loading/empty/error states required by the acceptance criteria. Errors are shown by their stable
 * backend code (e.g. {@code account.document.duplicate}).
 */
@Component({
  selector: 'app-accounts-page',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './accounts-page.html',
})
export class AccountsPage implements OnInit {
  private readonly accountsService = inject(AccountsService);

  readonly legalTypes: LegalType[] = ['CNPJ', 'MEI', 'CPF'];
  readonly statuses: AccountStatus[] = ['ACTIVE', 'SUSPENDED', 'INACTIVE'];

  readonly state = signal<ViewState>('loading');
  readonly accounts = signal<AccountResponse[]>([]);
  readonly errorCode = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  filter: AccountStatus | '' = '';
  legalType: LegalType = 'CNPJ';
  documentNumber = '';
  displayName = '';
  cadastur = '';
  iata = '';

  ngOnInit(): void {
    this.load();
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
}
