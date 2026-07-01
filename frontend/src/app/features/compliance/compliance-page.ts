import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { CadastroLabelPipe } from '../../core/cadastro/cadastro-label.pipe';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { ScreenState } from '../../shared/screen-state/screen-state';
import {
  CloseCheckView,
  DocumentType,
  DocumentView,
  SignedFormat,
} from './compliance.models';
import { ComplianceService } from './compliance.service';

/**
 * Compliance screen (SPEC-0008, SPEC-0029 16a): runs a period close-check (whether it may close and,
 * if not, the blocking entries and what each is missing), reads a vault document's metadata by id
 * (type/hash/issue/retention/personal-data — the fileRef is never exposed) and uploads a new document
 * (multipart). Retention is computed at ingestion by the backend. Each data section uses
 * {@link ScreenState}.
 */
@Component({
  selector: 'app-compliance-page',
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
    CadastroLabelPipe,
  ],
  templateUrl: './compliance-page.html',
})
export class CompliancePage implements FormLeaveGuard {
  private readonly complianceService = inject(ComplianceService);

  /** Whether the upload form holds an unsent file (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.uploading() && (this.uploadFile !== null || !!this.uploadIssuedAt);
  }

  readonly documentTypes: DocumentType[] = [
    'NFE',
    'NFSE',
    'RPA',
    'UTILITY_BILL',
    'LOAN_CONTRACT',
    'COMMISSION_INVOICE',
    'PAYMENT_PROOF',
    'REFUND_PROOF',
    'PAYROLL',
    'TIME_RECORD_AFD',
    'PROCESSED_JOURNAL_AEJ',
    'VOUCHER',
    'REPRESENTATION_CONTRACT',
    'OTHER',
  ];
  readonly signedFormats: SignedFormat[] = ['CAdES_P7S', 'XADES', 'PADES'];

  // Close-check state.
  periodQuery = '';
  readonly closeCheck = signal<CloseCheckView | null>(null);
  readonly checkState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly checkError = signal<string | null>(null);

  // Document lookup state.
  documentId = '';
  readonly document = signal<DocumentView | null>(null);
  readonly docState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly docError = signal<string | null>(null);

  // Upload form.
  uploadType: DocumentType = 'NFE';
  uploadIssuedAt = '';
  uploadSignedFormat: SignedFormat | '' = '';
  uploadHasPersonalData = false;
  uploadFile: File | null = null;
  readonly uploading = signal(false);
  readonly uploadError = signal<string | null>(null);

  /** Runs the period close-check. */
  runCloseCheck(): void {
    const period = this.periodQuery.trim();
    if (!period) {
      return;
    }
    this.checkState.set('loading');
    this.checkError.set(null);
    this.complianceService.closeCheck(period).subscribe({
      next: (view) => {
        this.closeCheck.set(view);
        this.checkState.set('success');
      },
      error: (error: ApiError) => {
        this.checkError.set(error?.code ?? 'error.internal');
        this.checkState.set('error');
      },
    });
  }

  /** Looks up a vault document's metadata by id. */
  lookupDocument(): void {
    const id = this.documentId.trim();
    if (!id) {
      return;
    }
    this.docState.set('loading');
    this.docError.set(null);
    this.complianceService.getDocument(id).subscribe({
      next: (doc) => {
        this.document.set(doc);
        this.docState.set('success');
      },
      error: (error: ApiError) => {
        this.docError.set(error?.code ?? 'error.internal');
        this.docState.set('error');
      },
    });
  }

  /** Captures the selected file from the native input. */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.uploadFile = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  /** Uploads the selected document to the vault. */
  upload(): void {
    if (!this.uploadFile || !this.uploadIssuedAt) {
      return;
    }
    this.uploading.set(true);
    this.uploadError.set(null);
    this.complianceService
      .upload({
        file: this.uploadFile,
        type: this.uploadType,
        issuedAt: this.uploadIssuedAt,
        signedFormat: this.uploadSignedFormat || null,
        hasPersonalData: this.uploadHasPersonalData,
      })
      .subscribe({
        next: (doc) => {
          this.uploading.set(false);
          this.document.set(doc);
          this.documentId = doc.id;
          this.docState.set('success');
        },
        error: (error: ApiError) => {
          this.uploading.set(false);
          this.uploadError.set(error?.code ?? 'error.internal');
        },
      });
  }
}
