import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  DefineRuleRequest,
  IssueDirectiveRequest,
  ParameterLayer,
  ParameterRuleResponse,
  ResolvedParameterResponse,
} from './commercial-policy.models';

/** Scope of a parameter resolution query (SPEC-0014 BR3). */
export interface ResolveScope {
  key: string;
  accountId?: string;
  productRef?: string;
  channel?: string;
}

/** Optional filters for the rules audit list (SPEC-0014). */
export interface RuleFilter {
  key?: string;
  layer?: ParameterLayer | '';
}

/** Feature API service for the CommercialPolicy governed parameters (SPEC-0014). */
@Injectable({ providedIn: 'root' })
export class CommercialPolicyService {
  private readonly http = inject(HttpClient);

  /** Resolves a governed parameter for a scope, returning value + provenance (BR2). */
  resolve(scope: ResolveScope): Observable<ResolvedParameterResponse> {
    let params = new HttpParams().set('key', scope.key);
    if (scope.accountId) {
      params = params.set('accountId', scope.accountId);
    }
    if (scope.productRef) {
      params = params.set('productRef', scope.productRef);
    }
    if (scope.channel) {
      params = params.set('channel', scope.channel);
    }
    return this.http.get<ResolvedParameterResponse>(`${API_BASE_URL}/commercial-policy/resolve`, {
      params,
    });
  }

  /** Lists rules for audit/curation, optionally filtered by key and/or layer. */
  listRules(filter: RuleFilter = {}): Observable<ParameterRuleResponse[]> {
    let params = new HttpParams();
    if (filter.key) {
      params = params.set('key', filter.key);
    }
    if (filter.layer) {
      params = params.set('layer', filter.layer);
    }
    return this.http.get<ParameterRuleResponse[]>(`${API_BASE_URL}/commercial-policy/rules`, {
      params,
    });
  }

  /** Defines a POLICY/PROMOTION/CONTRACT rule (admin/curator role; audited). */
  defineRule(request: DefineRuleRequest): Observable<ParameterRuleResponse> {
    return this.http.post<ParameterRuleResponse>(
      `${API_BASE_URL}/commercial-policy/rules`,
      request,
    );
  }

  /** Issues a director's directive (director role + justification; reinforced audit — BR5). */
  issueDirective(request: IssueDirectiveRequest): Observable<ParameterRuleResponse> {
    return this.http.post<ParameterRuleResponse>(
      `${API_BASE_URL}/commercial-policy/directives`,
      request,
    );
  }
}
