import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ApiError } from '../../core/http/api-error';
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import {
  BrandStatus,
  BrandView,
  ContractCoverage,
  ContractView,
  GoalMetric,
  GoalProgress,
} from './portfolio.models';
import { PortfolioService } from './portfolio.service';

/**
 * Portfolio screen (SPEC-0020, SPEC-0029 16c): represented brands, representation contracts and goals
 * vs realized. Lists/registers/deactivates brands; lists a brand's contracts and checks coverage on a
 * date (a read-model alert, never a block — BR2); defines a VOLUME/REVENUE goal and reads its progress
 * (target × realized × attainment %). Each data section uses {@link ScreenState} for the loading/empty/
 * error/permission states.
 */
@Component({
  selector: 'app-portfolio-page',
  imports: [
    FormsModule,
    TranslatePipe,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TagModule,
    ScreenState,
  ],
  templateUrl: './portfolio-page.html',
})
export class PortfolioPage implements OnInit, FormLeaveGuard {
  private readonly portfolioService = inject(PortfolioService);

  readonly statuses: BrandStatus[] = ['ACTIVE', 'INACTIVE'];
  readonly metrics: GoalMetric[] = ['VOLUME', 'REVENUE'];

  readonly format = formatMoney;

  // Brand list.
  filterStatus: BrandStatus | '' = '';
  readonly listState = signal<'loading' | 'success' | 'error'>('loading');
  readonly brands = signal<BrandView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly brandsState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'success') {
      return this.brands().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Register brand form.
  newBrandRef = '';
  newDisplayName = '';
  readonly registerBusy = signal(false);
  readonly registerError = signal<string | null>(null);

  // Contracts for a brand.
  contractBrandRef = '';
  readonly contractsState = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  readonly contracts = signal<ContractView[]>([]);
  readonly contractsError = signal<string | null>(null);
  readonly coverage = signal<ContractCoverage | null>(null);

  readonly contractsListState = computed<ScreenStateKind>(() => {
    const s = this.contractsState();
    if (s === 'idle') {
      return 'empty';
    }
    if (s === 'success') {
      return this.contracts().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Register contract form.
  newContractValidFrom = '';
  newContractValidUntil = '';
  newContractDocumentId = '';
  readonly contractBusy = signal(false);
  readonly contractError = signal<string | null>(null);

  // Goals.
  goalBrandRef = '';
  goalBrandId = '';
  goalPeriod = '';
  goalMetric: GoalMetric = 'REVENUE';
  goalTargetAmount: number | null = null;
  goalTargetCurrency = 'BRL';
  goalTargetCount: number | null = null;
  readonly goalBusy = signal(false);
  readonly goalError = signal<string | null>(null);
  readonly progress = signal<GoalProgress | null>(null);
  readonly progressError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadBrands();
  }

  /** Whether an authoring form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.registerBusy() && (!!this.newBrandRef || !!this.newDisplayName);
  }

  /** (Re)loads the brand list using the current status filter. */
  loadBrands(): void {
    this.listState.set('loading');
    this.portfolioService.listBrands(this.filterStatus).subscribe({
      next: (brands) => {
        this.brands.set(brands);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Registers a brand and reloads the list. */
  registerBrand(): void {
    if (!this.newBrandRef || !this.newDisplayName) {
      return;
    }
    this.registerBusy.set(true);
    this.registerError.set(null);
    this.portfolioService
      .registerBrand({ brandRef: this.newBrandRef, displayName: this.newDisplayName })
      .subscribe({
        next: () => {
          this.registerBusy.set(false);
          this.newBrandRef = '';
          this.newDisplayName = '';
          this.loadBrands();
        },
        error: (error: ApiError) => {
          this.registerBusy.set(false);
          this.registerError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Deactivates a brand and reloads the list. */
  deactivateBrand(brand: BrandView): void {
    this.portfolioService.deactivateBrand(brand.id).subscribe({
      next: () => this.loadBrands(),
      error: (error: ApiError) => this.errorCode.set(error?.code ?? 'error.internal'),
    });
  }

  /** Loads a brand's contracts and checks its current coverage. */
  loadContracts(): void {
    const ref = this.contractBrandRef.trim();
    if (!ref) {
      return;
    }
    this.contractsState.set('loading');
    this.contractsError.set(null);
    this.portfolioService.contracts(ref).subscribe({
      next: (contracts) => {
        this.contracts.set(contracts);
        this.contractsState.set('success');
      },
      error: (error: ApiError) => {
        this.contractsError.set(error?.code ?? 'error.internal');
        this.contractsState.set('error');
      },
    });
    this.portfolioService.contractCoverage(ref).subscribe({
      next: (coverage) => this.coverage.set(coverage),
      error: () => this.coverage.set(null),
    });
  }

  /** Registers a contract for the entered brand and reloads the contracts. */
  registerContract(): void {
    const ref = this.contractBrandRef.trim();
    if (!ref || !this.newContractValidFrom) {
      return;
    }
    this.contractBusy.set(true);
    this.contractError.set(null);
    this.portfolioService
      .registerContract(ref, {
        validFrom: this.newContractValidFrom,
        validUntil: this.newContractValidUntil || null,
        documentId: this.newContractDocumentId || null,
      })
      .subscribe({
        next: () => {
          this.contractBusy.set(false);
          this.newContractValidUntil = '';
          this.newContractDocumentId = '';
          this.loadContracts();
        },
        error: (error: ApiError) => {
          this.contractBusy.set(false);
          this.contractError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Defines a goal for the entered brand. */
  defineGoal(): void {
    const ref = this.goalBrandRef.trim();
    if (!ref || !this.goalPeriod) {
      return;
    }
    this.goalBusy.set(true);
    this.goalError.set(null);
    const target =
      this.goalMetric === 'REVENUE' && this.goalTargetAmount != null
        ? { amount: this.goalTargetAmount, currency: this.goalTargetCurrency }
        : null;
    this.portfolioService
      .defineGoal(ref, {
        period: this.goalPeriod,
        metric: this.goalMetric,
        target,
        targetCount: this.goalMetric === 'VOLUME' ? this.goalTargetCount : null,
      })
      .subscribe({
        next: (goal) => {
          this.goalBusy.set(false);
          this.goalBrandId = goal.id;
          this.loadProgress();
        },
        error: (error: ApiError) => {
          this.goalBusy.set(false);
          this.goalError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Reads a goal's progress (target × realized × attainment). */
  loadProgress(): void {
    const id = this.goalBrandId.trim();
    if (!id || !this.goalPeriod) {
      return;
    }
    this.progressError.set(null);
    this.portfolioService.goalProgress(id, this.goalPeriod).subscribe({
      next: (progress) => this.progress.set(progress),
      error: (error: ApiError) => this.progressError.set(error?.code ?? 'error.internal'),
    });
  }

  /** PrimeNG Tag severity for a brand status. */
  statusSeverity(status: BrandStatus): 'success' | 'secondary' {
    return status === 'ACTIVE' ? 'success' : 'secondary';
  }
}
