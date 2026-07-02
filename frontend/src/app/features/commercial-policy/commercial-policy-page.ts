import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  ParameterLayer,
  ParameterRuleResponse,
  ParameterValueType,
  ResolvedParameterResponse,
} from './commercial-policy.models';
import { CommercialPolicyService } from './commercial-policy.service';

/**
 * CommercialPolicy screen (SPEC-0014, SPEC-0029 16c): governed parameters + directives. Resolves a
 * parameter for a scope showing its winning value and provenance (which layer won, who/when), lists
 * the governed rules for audit with the fixed precedence Diretiva > Promoção > Contrato > Política >
 * Padrão (BR2), defines a POLICY/PROMOTION/CONTRACT rule (curator/DIRECTOR — POLICY_ADMIN role) and
 * issues a director's directive (top of precedence; a mandatory justification; DIRECTOR role — BR5).
 * The backend is the authority: a caller without the role gets a 403 that the {@link ScreenState} /
 * the form error code renders as a permission message, never a generic error.
 */
@Component({
  selector: 'app-commercial-policy-page',
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
  templateUrl: './commercial-policy-page.html',
})
export class CommercialPolicyPage implements OnInit, FormLeaveGuard {
  private readonly policyService = inject(CommercialPolicyService);

  /** The fixed precedence order (BR2), most-authoritative first — shown as an explainer. */
  readonly precedence: ParameterLayer[] = [
    'DIRECTIVE',
    'PROMOTION',
    'CONTRACT',
    'POLICY',
    'SYSTEM_DEFAULT',
  ];
  readonly ruleLayers: ParameterLayer[] = ['POLICY', 'PROMOTION', 'CONTRACT'];
  readonly valueTypes: ParameterValueType[] = ['NUMBER', 'PERCENT', 'MONEY', 'BOOL'];

  // Resolve section.
  resolveKey = 'MARKUP_PCT';
  resolveAccountId = '';
  resolveProductRef = '';
  resolveChannel = '';
  readonly resolveState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly resolved = signal<ResolvedParameterResponse | null>(null);
  readonly resolveError = signal<string | null>(null);

  // Rules audit list.
  filterKey = '';
  filterLayer: ParameterLayer | '' = '';
  readonly listState = signal<'loading' | 'success' | 'error'>('loading');
  readonly rules = signal<ParameterRuleResponse[]>([]);
  readonly listError = signal<string | null>(null);

  readonly rulesState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'success') {
      return this.rules().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Define rule form.
  ruleKey = '';
  ruleLayer: ParameterLayer = 'POLICY';
  ruleValue = '';
  ruleType: ParameterValueType = 'PERCENT';
  ruleProductRef = '';
  ruleChannel = '';
  readonly ruleBusy = signal(false);
  readonly ruleError = signal<string | null>(null);

  // Issue directive form.
  directiveKey = '';
  directiveValue = '';
  directiveType: ParameterValueType = 'PERCENT';
  directiveJustification = '';
  readonly directiveBusy = signal(false);
  readonly directiveError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadRules();
  }

  /** Whether an authoring form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return (
      (!this.ruleBusy() && (!!this.ruleKey || !!this.ruleValue)) ||
      (!this.directiveBusy() && (!!this.directiveKey || !!this.directiveValue))
    );
  }

  /** Resolves a governed parameter for the entered scope, showing the winning value + provenance. */
  resolve(): void {
    const key = this.resolveKey.trim();
    if (!key) {
      return;
    }
    this.resolveState.set('loading');
    this.resolveError.set(null);
    this.policyService
      .resolve({
        key,
        accountId: this.resolveAccountId || undefined,
        productRef: this.resolveProductRef || undefined,
        channel: this.resolveChannel || undefined,
      })
      .subscribe({
        next: (result) => {
          this.resolved.set(result);
          this.resolveState.set('success');
        },
        error: (error: ApiError) => {
          this.resolveError.set(error?.code ?? 'error.internal');
          this.resolveState.set('error');
        },
      });
  }

  /** (Re)loads the rules audit list using the current filters. */
  loadRules(): void {
    this.listState.set('loading');
    this.policyService.listRules({ key: this.filterKey, layer: this.filterLayer }).subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.listError.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Defines a governed rule (POLICY/PROMOTION/CONTRACT; needs the curator/director role). */
  defineRule(): void {
    if (!this.ruleKey || !this.ruleValue) {
      return;
    }
    this.ruleBusy.set(true);
    this.ruleError.set(null);
    this.policyService
      .defineRule({
        key: this.ruleKey,
        layer: this.ruleLayer,
        value: this.ruleValue,
        type: this.ruleType,
        productRef: this.ruleProductRef || null,
        channel: this.ruleChannel || null,
      })
      .subscribe({
        next: () => {
          this.ruleBusy.set(false);
          this.ruleKey = '';
          this.ruleValue = '';
          this.loadRules();
        },
        error: (error: ApiError) => {
          this.ruleBusy.set(false);
          this.ruleError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Issues a director's directive (top of precedence; mandatory justification; DIRECTOR role). */
  issueDirective(): void {
    if (!this.directiveKey || !this.directiveValue || !this.directiveJustification) {
      return;
    }
    this.directiveBusy.set(true);
    this.directiveError.set(null);
    this.policyService
      .issueDirective({
        key: this.directiveKey,
        value: this.directiveValue,
        type: this.directiveType,
        justification: this.directiveJustification,
      })
      .subscribe({
        next: () => {
          this.directiveBusy.set(false);
          this.directiveKey = '';
          this.directiveValue = '';
          this.directiveJustification = '';
          this.loadRules();
        },
        error: (error: ApiError) => {
          this.directiveBusy.set(false);
          this.directiveError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** PrimeNG Tag severity for a governance layer (top of precedence highlighted). */
  layerSeverity(layer: string): 'danger' | 'warn' | 'info' | 'secondary' {
    switch (layer) {
      case 'DIRECTIVE':
        return 'danger';
      case 'PROMOTION':
        return 'warn';
      case 'CONTRACT':
        return 'info';
      default:
        return 'secondary';
    }
  }
}
