import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { CloseCheckView, DocumentView, UploadDocumentRequest } from './compliance.models';

/** Feature API service for the Compliance document vault + close-check (SPEC-0008). */
@Injectable({ providedIn: 'root' })
export class ComplianceService {
  private readonly http = inject(HttpClient);

  /** Runs the period close-check (whether it may close and the blocking entries). */
  closeCheck(period: string): Observable<CloseCheckView> {
    const params = new HttpParams().set('period', period);
    return this.http.get<CloseCheckView>(`${API_BASE_URL}/compliance/close-check`, { params });
  }

  /** Reads a vault document's metadata by id (fileRef is never exposed). */
  getDocument(id: string): Observable<DocumentView> {
    return this.http.get<DocumentView>(`${API_BASE_URL}/compliance/documents/${id}`);
  }

  /** Uploads a document to the vault (multipart). Retention is computed at ingestion. */
  upload(request: UploadDocumentRequest): Observable<DocumentView> {
    const form = new FormData();
    form.append('file', request.file);
    form.append('type', request.type);
    form.append('issuedAt', request.issuedAt);
    form.append('hasPersonalData', String(request.hasPersonalData));
    if (request.signedFormat) {
      form.append('signedFormat', request.signedFormat);
    }
    return this.http.post<DocumentView>(`${API_BASE_URL}/compliance/documents`, form);
  }
}
