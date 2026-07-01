import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { PageResponse } from '../../core/models/api.models';
import { PointCrawlRunView, PointSnapshotView } from './point.models';
import { PointPage } from './point-page';
import { PointService } from './point.service';

const RUN: PointCrawlRunView = {
  id: 'r1',
  sourceRef: 'REP-1',
  periodRef: '2026-01',
  status: 'SUCCEEDED',
  attempts: 1,
  items: 30,
  failures: 0,
  failureClass: null,
  startedAt: '2026-01-31T00:00:00Z',
  finishedAt: '2026-01-31T00:01:00Z',
  correlationId: 'c1',
};

const SNAPSHOT: PointSnapshotView = {
  id: 's1',
  sourceRef: 'REP-1',
  periodRef: '2026-01',
  operationalOnly: true,
  marks: 42,
  collectedAt: '2026-01-31T00:00:00Z',
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function configure(service: Partial<PointService>): void {
  TestBed.configureTestingModule({
    imports: [PointPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: PointService, useValue: service },
    ],
  });
}

describe('PointPage', () => {
  it('loads the run history (loading → success)', () => {
    configure({ runs: () => of(page([RUN])) });
    const fixture = TestBed.createComponent(PointPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.runsState()).toBe('success');
    expect(fixture.componentInstance.runs().length).toBe(1);
  });

  it('shows the empty state when there are no runs', () => {
    configure({ runs: () => of(page<PointCrawlRunView>([])) });
    const fixture = TestBed.createComponent(PointPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.runsState()).toBe('empty');
  });

  it('shows the error state when the run history fails', () => {
    configure({
      runs: () => throwError(() => ({ code: 'error.internal', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PointPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.runsState()).toBe('error');
  });

  it('renders the permission state on a 403 run history', () => {
    configure({
      runs: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PointPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('reads a snapshot by id', () => {
    configure({ runs: () => of(page<PointCrawlRunView>([])), snapshot: () => of(SNAPSHOT) });
    const fixture = TestBed.createComponent(PointPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.snapshotId = 's1';
    component.loadSnapshot();

    expect(component.snapshot()?.marks).toBe(42);
  });

  it('surfaces a snapshot error by its code', () => {
    configure({
      runs: () => of(page<PointCrawlRunView>([])),
      snapshot: () => throwError(() => ({ code: 'point.snapshot.notfound', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(PointPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.snapshotId = 's1';
    component.loadSnapshot();

    expect(component.snapshotError()).toBe('point.snapshot.notfound');
  });

  it('maps crawl-run status severities', () => {
    configure({ runs: () => of(page<PointCrawlRunView>([])) });
    const component = TestBed.createComponent(PointPage).componentInstance;

    expect(component.statusSeverity('SUCCEEDED')).toBe('success');
    expect(component.statusSeverity('RUNNING')).toBe('info');
    expect(component.statusSeverity('RETRY_SCHEDULED')).toBe('warn');
    expect(component.statusSeverity('DEAD_LETTER')).toBe('danger');
  });
});
