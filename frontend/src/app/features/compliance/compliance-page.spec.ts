import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { CompliancePage } from './compliance-page';
import { CloseCheckView, DocumentView } from './compliance.models';
import { ComplianceService } from './compliance.service';

const DOC: DocumentView = {
  id: 'd1',
  type: 'NFE',
  hash: 'sha256:abc',
  issuedAt: '2026-06-01',
  retentionUntil: '2031-06-01',
  signedFormat: null,
  hasPersonalData: false,
  createdAt: '2026-06-01T00:00:00Z',
};

const CANNOT_CLOSE: CloseCheckView = {
  period: '2026-06',
  canClose: false,
  pending: [{ entryId: 'e1', entryType: 'COMMISSION_PAYABLE', missing: ['NFSE'] }],
};

function configure(service: Partial<ComplianceService>): void {
  TestBed.configureTestingModule({
    imports: [CompliancePage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: ComplianceService, useValue: service },
    ],
  });
}

describe('CompliancePage', () => {
  it('starts idle before any query (empty screen)', () => {
    configure({});
    const fixture = TestBed.createComponent(CompliancePage);

    fixture.detectChanges();

    expect(fixture.componentInstance.checkState()).toBe('idle');
    expect(fixture.componentInstance.docState()).toBe('idle');
  });

  it('runs the close-check and reports the pending entries (loading → success)', () => {
    configure({ closeCheck: () => of(CANNOT_CLOSE) });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    page.periodQuery = '2026-06';
    page.runCloseCheck();

    expect(page.checkState()).toBe('success');
    expect(page.closeCheck()?.canClose).toBe(false);
    expect(page.closeCheck()?.pending.length).toBe(1);
  });

  it('reads a document by id and exposes its metadata', () => {
    configure({ getDocument: () => of(DOC) });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    page.documentId = 'd1';
    page.lookupDocument();

    expect(page.docState()).toBe('success');
    expect(page.document()?.hash).toBe('sha256:abc');
  });

  it('shows the error state when the document lookup fails', () => {
    configure({
      getDocument: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    page.documentId = 'd-x';
    page.lookupDocument();

    expect(page.docState()).toBe('error');
    expect(page.docError()).toBe('error.internal');
  });

  it('reports a period that may close', () => {
    configure({ closeCheck: () => of({ period: '2026-06', canClose: true, pending: [] }) });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    page.periodQuery = '2026-06';
    page.runCloseCheck();
    fixture.detectChanges();

    expect(page.closeCheck()?.canClose).toBe(true);
  });

  it('captures a selected file and uploads it (dirty form is tracked)', () => {
    const upload = vi.fn(() => of(DOC));
    configure({ upload });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    const file = new File(['abc'], 'nf.xml', { type: 'application/xml' });
    page.onFileSelected({ target: { files: [file] } } as unknown as Event);
    page.uploadIssuedAt = '2026-06-01';
    expect(page.isDirty()).toBe(true);
    page.upload();
    fixture.detectChanges();

    expect(upload).toHaveBeenCalled();
    expect(page.document()?.id).toBe('d1');
    expect(page.docState()).toBe('success');
  });

  it('clears the captured file when the input has none', () => {
    configure({});
    const page = TestBed.createComponent(CompliancePage).componentInstance;

    page.onFileSelected({ target: { files: [] } } as unknown as Event);

    expect(page.isDirty()).toBe(false);
  });

  it('surfaces an upload error by its code', () => {
    configure({
      upload: () =>
        throwError(() => ({ code: 'compliance.upload.invalid', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    const file = new File(['abc'], 'nf.xml');
    page.onFileSelected({ target: { files: [file] } } as unknown as Event);
    page.uploadIssuedAt = '2026-06-01';
    page.upload();

    expect(page.uploadError()).toBe('compliance.upload.invalid');
  });

  it('renders the permission state on a 403 close-check', () => {
    configure({
      closeCheck: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CompliancePage);
    const page = fixture.componentInstance;

    page.periodQuery = '2026-06';
    page.runCloseCheck();

    expect(page.checkState()).toBe('error');
    expect(page.checkError()).toBe('access.denied');
  });
});
