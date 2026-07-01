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
import { FormLeaveGuard } from '../../core/guards/can-deactivate.guard';
import { ApiError } from '../../core/http/api-error';
import { formatMoney } from '../../core/models/api.models';
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { AssetStatus, AssetType, AssetView } from './assets.models';
import { AssetsService } from './assets.service';

/**
 * Assets screen (SPEC-0021, SPEC-0029 16d): internal patrimony — equipment and software licenses.
 * Lists assets (combinable type/status/expiring filters), registers an asset, retires one with an
 * audited reason and runs the license-expiry sweep. Amounts are shown in the original currency
 * (never client math). Assets never prices a sale (BR5). Each data section uses {@link ScreenState}.
 */
@Component({
  selector: 'app-assets-page',
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
  templateUrl: './assets-page.html',
})
export class AssetsPage implements OnInit, FormLeaveGuard {
  private readonly assetsService = inject(AssetsService);

  readonly types: AssetType[] = ['EQUIPMENT', 'SOFTWARE_LICENSE', 'OTHER'];
  readonly statuses: AssetStatus[] = ['ACTIVE', 'RETIRED'];
  readonly format = formatMoney;

  // Asset list.
  filterType: AssetType | '' = '';
  filterStatus: AssetStatus | '' = '';
  filterExpiring: number | null = null;
  readonly listState = signal<'loading' | 'success' | 'error'>('loading');
  readonly assets = signal<AssetView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly assetsState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'success') {
      return this.assets().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Register asset form.
  newType: AssetType = 'EQUIPMENT';
  newIdentifier = '';
  newAcquisitionDate = '';
  newCostAmount: number | null = null;
  newCostCurrency = 'BRL';
  newExpiresAt = '';
  newSupplierRef = '';
  readonly registerBusy = signal(false);
  readonly registerError = signal<string | null>(null);

  // Retire form.
  retireReason = '';
  readonly retireError = signal<string | null>(null);

  // Sweep.
  readonly sweepBusy = signal(false);
  readonly sweepResult = signal<number | null>(null);

  ngOnInit(): void {
    this.loadAssets();
  }

  /** Whether the register form holds unsaved input (SPEC-0026 BR9). */
  isDirty(): boolean {
    return !this.registerBusy() && (!!this.newIdentifier || !!this.newAcquisitionDate);
  }

  /** (Re)loads the asset list using the current filters. */
  loadAssets(): void {
    this.listState.set('loading');
    this.assetsService.list(this.filterType, this.filterStatus, this.filterExpiring).subscribe({
      next: (assets) => {
        this.assets.set(assets);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Registers an asset and reloads the list. */
  registerAsset(): void {
    if (!this.newIdentifier || !this.newAcquisitionDate || this.newCostAmount == null) {
      return;
    }
    this.registerBusy.set(true);
    this.registerError.set(null);
    this.assetsService
      .register({
        type: this.newType,
        identifier: this.newIdentifier,
        acquisitionDate: this.newAcquisitionDate,
        acquisitionCost: { amount: this.newCostAmount, currency: this.newCostCurrency },
        expiresAt: this.newExpiresAt || null,
        supplierRef: this.newSupplierRef || null,
      })
      .subscribe({
        next: () => {
          this.registerBusy.set(false);
          this.newIdentifier = '';
          this.newAcquisitionDate = '';
          this.newCostAmount = null;
          this.newExpiresAt = '';
          this.newSupplierRef = '';
          this.loadAssets();
        },
        error: (error: ApiError) => {
          this.registerBusy.set(false);
          this.registerError.set(error?.code ?? 'error.internal');
        },
      });
  }

  /** Retires an asset with the entered reason and reloads the list. */
  retireAsset(asset: AssetView): void {
    const reason = this.retireReason.trim() || 'retired';
    this.retireError.set(null);
    this.assetsService.retire(asset.id, { reason }).subscribe({
      next: () => {
        this.retireReason = '';
        this.loadAssets();
      },
      error: (error: ApiError) => this.retireError.set(error?.code ?? 'error.internal'),
    });
  }

  /** Runs the license-expiry sweep and shows how many were flagged. */
  runSweep(): void {
    this.sweepBusy.set(true);
    this.sweepResult.set(null);
    this.assetsService.flagExpiring().subscribe({
      next: (result) => {
        this.sweepBusy.set(false);
        this.sweepResult.set(result.flagged);
        this.loadAssets();
      },
      error: (error: ApiError) => {
        this.sweepBusy.set(false);
        this.errorCode.set(error?.code ?? 'error.internal');
      },
    });
  }

  /** PrimeNG Tag severity for an asset status. */
  statusSeverity(status: AssetStatus): 'success' | 'secondary' {
    return status === 'ACTIVE' ? 'success' : 'secondary';
  }
}
