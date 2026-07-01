import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  CadastroItemView,
  CadastroType,
  CreateCadastroItemRequest,
  UpdateCadastroItemRequest,
} from './cadastro.models';

/**
 * Feature API service for the Cadastro module (SPEC-0031 — editable reference data). Reads require
 * authentication; writes require ROLE_POLICY_ADMIN (gated in SecurityConfig, DL-0115). The backend
 * is the authority, so a caller without the role gets a 403 rendered as the permission state.
 */
@Injectable({ providedIn: 'root' })
export class CadastroService {
  private readonly http = inject(HttpClient);

  /** Lists the convertible cadastro types. */
  listTypes(): Observable<CadastroType[]> {
    return this.http.get<CadastroType[]>(`${API_BASE_URL}/cadastro/types`);
  }

  /** Lists the items of a type (active first, by sort order). */
  listItems(type: CadastroType): Observable<CadastroItemView[]> {
    const params = new HttpParams().set('type', type);
    return this.http.get<CadastroItemView[]>(`${API_BASE_URL}/cadastro/items`, { params });
  }

  /** Creates a new item (ROLE_POLICY_ADMIN). */
  create(request: CreateCadastroItemRequest): Observable<CadastroItemView> {
    return this.http.post<CadastroItemView>(`${API_BASE_URL}/cadastro/items`, request);
  }

  /** Updates the editable fields of an item (ROLE_POLICY_ADMIN). */
  update(id: string, request: UpdateCadastroItemRequest): Observable<CadastroItemView> {
    return this.http.put<CadastroItemView>(`${API_BASE_URL}/cadastro/items/${id}`, request);
  }

  /** Deactivates an item — soft delete (ROLE_POLICY_ADMIN). */
  deactivate(id: string): Observable<CadastroItemView> {
    return this.http.delete<CadastroItemView>(`${API_BASE_URL}/cadastro/items/${id}`);
  }
}
