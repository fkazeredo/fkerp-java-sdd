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
import { ScreenState, ScreenStateKind } from '../../shared/screen-state/screen-state';
import { CrawlRunStatus, PointCrawlRunView, PointSnapshotView } from './point.models';
import { PointService } from './point.service';

/**
 * Ponto (point-clock) operational screen (SPEC-0012, SPEC-0029 16d): the crawl-run execution history
 * (governed collection with attempts/failure-class) and an operational snapshot lookup by id. Read
 * only — the AFD/AEJ signed ingest and the manual crawl trigger are M2M/operational actions and stay
 * out of the operator UI (DL-0109). Each data section uses {@link ScreenState}.
 */
@Component({
  selector: 'app-point-page',
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
  templateUrl: './point-page.html',
})
export class PointPage implements OnInit {
  private readonly pointService = inject(PointService);

  readonly statuses: CrawlRunStatus[] = [
    'RUNNING',
    'SUCCEEDED',
    'RETRY_SCHEDULED',
    'DEAD_LETTER',
  ];

  // Crawl-run history.
  filterStatus: CrawlRunStatus | '' = '';
  readonly listState = signal<'loading' | 'success' | 'error'>('loading');
  readonly runs = signal<PointCrawlRunView[]>([]);
  readonly errorCode = signal<string | null>(null);

  readonly runsState = computed<ScreenStateKind>(() => {
    const s = this.listState();
    if (s === 'success') {
      return this.runs().length === 0 ? 'empty' : 'success';
    }
    return s;
  });

  // Snapshot lookup.
  snapshotId = '';
  readonly snapshot = signal<PointSnapshotView | null>(null);
  readonly snapshotError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadRuns();
  }

  /** (Re)loads the crawl-run history using the current status filter. */
  loadRuns(): void {
    this.listState.set('loading');
    this.pointService.runs(this.filterStatus).subscribe({
      next: (page) => {
        this.runs.set(page.content);
        this.listState.set('success');
      },
      error: (error: ApiError) => {
        this.errorCode.set(error?.code ?? 'error.internal');
        this.listState.set('error');
      },
    });
  }

  /** Reads an operational snapshot by id. */
  loadSnapshot(): void {
    const id = this.snapshotId.trim();
    if (!id) {
      return;
    }
    this.snapshotError.set(null);
    this.snapshot.set(null);
    this.pointService.snapshot(id).subscribe({
      next: (snapshot) => this.snapshot.set(snapshot),
      error: (error: ApiError) => this.snapshotError.set(error?.code ?? 'error.internal'),
    });
  }

  /** PrimeNG Tag severity for a crawl-run status. */
  statusSeverity(status: CrawlRunStatus): 'success' | 'info' | 'warn' | 'danger' {
    switch (status) {
      case 'SUCCEEDED':
        return 'success';
      case 'RUNNING':
        return 'info';
      case 'RETRY_SCHEDULED':
        return 'warn';
      default:
        return 'danger';
    }
  }
}
