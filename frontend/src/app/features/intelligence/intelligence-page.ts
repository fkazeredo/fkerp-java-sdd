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
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  InsightDecision,
  InsightStatus,
  InsightType,
  InsightView,
} from './intelligence.models';
import { IntelligenceService } from './intelligence.service';

/**
 * Intelligence/DSS screen (SPEC-0013, SPEC-0029 16c): the insight panel. Lists prescriptive insights
 * filtered by type/subject/status (ordered by estimated gain), opens one to read its evidence (numbers
 * + provenance), recommendation (verdict/action/gain/risk) and the guardrail crossed (an alert, never
 * a block — BR3), and records the human decision (ACCEPTED/REJECTED/DISMISSED). Recording a decision
 * only registers it — the DSS advises, the human decides; it never triggers an automatic action (BR2).
 * Each data section uses {@link ScreenState} for the loading/empty/error/permission states.
 */
@Component({
  selector: 'app-intelligence-page',
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
  templateUrl: './intelligence-page.html',
})
export class IntelligencePage implements OnInit {
  private readonly intelligenceService = inject(IntelligenceService);

  readonly types: InsightType[] = ['PROMO_FX_ADVISOR', 'OVERRIDE_NUDGE'];
  readonly statuses: InsightStatus[] = ['NEW', 'ACCEPTED', 'REJECTED', 'DISMISSED'];
  readonly decisions: InsightDecision[] = ['ACCEPTED', 'REJECTED', 'DISMISSED'];

  readonly format = formatMoney;

  // List state.
  readonly state = signal<'loading' | 'success' | 'error'>('loading');
  readonly insights = signal<InsightView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly listState = computed<ScreenStateKind>(() => {
    const s = this.state();
    if (s === 'success') {
      return this.insights().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Filters.
  filterType: InsightType | '' = '';
  filterStatus: InsightStatus | '' = '';
  filterSubject = '';

  // Selection / detail + decision.
  readonly selected = signal<InsightView | null>(null);
  readonly decisionError = signal<string | null>(null);
  readonly busy = signal(false);
  decision: InsightDecision = 'ACCEPTED';
  decisionNote = '';

  ngOnInit(): void {
    this.load();
  }

  /** (Re)loads the insight list using the current filters. */
  load(): void {
    this.state.set('loading');
    this.intelligenceService
      .list({ type: this.filterType, status: this.filterStatus, subjectRef: this.filterSubject })
      .subscribe({
        next: (page) => {
          this.insights.set(page.content);
          this.state.set('success');
        },
        error: (error: ApiError) => {
          this.errorCode.set(error?.code ?? 'error.internal');
          this.state.set('error');
        },
      });
  }

  /** Selects an insight for the detail/decision panel. */
  select(insight: InsightView): void {
    this.selected.set(insight);
    this.decisionError.set(null);
  }

  /** Records the human decision on the selected insight (records only — never acts). */
  decide(): void {
    const current = this.selected();
    if (!current) {
      return;
    }
    this.busy.set(true);
    this.decisionError.set(null);
    this.intelligenceService
      .decide(current.id, { decision: this.decision, note: this.decisionNote || null })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.decisionNote = '';
          this.selected.set(updated);
          this.load();
        },
        error: (error: ApiError) => {
          this.busy.set(false);
          this.decisionError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** PrimeNG Tag severity for a decision status. */
  statusSeverity(status: InsightStatus): 'success' | 'info' | 'danger' | 'secondary' {
    switch (status) {
      case 'ACCEPTED':
        return 'success';
      case 'NEW':
        return 'info';
      case 'REJECTED':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  /** PrimeNG Tag severity for the advisor verdict. */
  verdictSeverity(verdict: string): 'success' | 'warn' {
    return verdict === 'CONVERTE' ? 'success' : 'warn';
  }
}
