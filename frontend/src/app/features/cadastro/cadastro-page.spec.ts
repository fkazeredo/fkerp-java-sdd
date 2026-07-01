import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideTranslateLoader, provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { InMemoryTranslateLoader } from '../../core/i18n/in-memory-translate.loader';
import { CadastroPage } from './cadastro-page';
import { CadastroItemView } from './cadastro.models';
import { CadastroService } from './cadastro.service';

const ITEM: CadastroItemView = {
  id: 'i1',
  type: 'ASSET_TYPE',
  code: 'EQUIPMENT',
  label: 'Equipamento',
  active: true,
  sortOrder: 10,
  createdAt: '2026-07-01T00:00:00Z',
};

function configure(service: Partial<CadastroService>): void {
  TestBed.configureTestingModule({
    imports: [CadastroPage],
    providers: [
      provideNoopAnimations(),
      provideTranslateService({
        lang: 'pt-BR',
        fallbackLang: 'pt-BR',
        loader: provideTranslateLoader(() => new InMemoryTranslateLoader()),
      }),
      { provide: CadastroService, useValue: service },
    ],
  });
}

describe('CadastroPage', () => {
  it('loads types and the first type items (loading → success)', () => {
    configure({ listTypes: () => of(['ASSET_TYPE', 'TAX_REGIME']), listItems: () => of([ITEM]) });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.types().length).toBe(2);
    expect(component.selectedType).toBe('ASSET_TYPE');
    expect(component.itemsState()).toBe('success');
    expect(component.items().length).toBe(1);
  });

  it('shows the empty state when a type has no items', () => {
    configure({ listTypes: () => of(['ASSET_TYPE']), listItems: () => of([]) });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.itemsState()).toBe('empty');
  });

  it('renders the permission state on a 403 items list', () => {
    configure({
      listTypes: () => of(['ASSET_TYPE']),
      listItems: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.itemsState()).toBe('error');
    expect(fixture.componentInstance.errorCode()).toBe('access.denied');
  });

  it('creates a new item and reloads', () => {
    const create = vi.fn(() => of(ITEM));
    const listItems = vi.fn(() => of([ITEM]));
    configure({ listTypes: () => of(['ASSET_TYPE']), listItems, create });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newCode = 'VEHICLE';
    component.newLabel = 'Veículo';
    component.createItem();

    expect(create).toHaveBeenCalled();
    expect(listItems).toHaveBeenCalledTimes(2);
  });

  it('surfaces a create error by its code (403 write)', () => {
    configure({
      listTypes: () => of(['ASSET_TYPE']),
      listItems: () => of([]),
      create: () => throwError(() => ({ code: 'access.denied', message: '', fields: [] })),
    });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.newCode = 'X';
    component.newLabel = 'Y';
    component.createItem();

    expect(component.createError()).toBe('access.denied');
  });

  it('deactivates an active item via toggle', () => {
    const deactivate = vi.fn(() => of({ ...ITEM, active: false }));
    configure({ listTypes: () => of(['ASSET_TYPE']), listItems: () => of([ITEM]), deactivate });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();

    fixture.componentInstance.toggleActive(ITEM);

    expect(deactivate).toHaveBeenCalledWith('i1');
  });

  it('reactivates an inactive item via toggle (update)', () => {
    const update = vi.fn(() => of(ITEM));
    configure({ listTypes: () => of(['ASSET_TYPE']), listItems: () => of([ITEM]), update });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();

    fixture.componentInstance.toggleActive({ ...ITEM, active: false });

    expect(update).toHaveBeenCalled();
  });

  it('saves an edited item', () => {
    const update = vi.fn(() => of(ITEM));
    configure({ listTypes: () => of(['ASSET_TYPE']), listItems: () => of([ITEM]), update });
    const fixture = TestBed.createComponent(CadastroPage);
    fixture.detectChanges();

    fixture.componentInstance.saveItem(ITEM);

    expect(update).toHaveBeenCalledWith('i1', { label: 'Equipamento', active: true, sortOrder: 10 });
  });

  it('maps active severities', () => {
    configure({ listTypes: () => of([]) });
    const component = TestBed.createComponent(CadastroPage).componentInstance;

    expect(component.activeSeverity(true)).toBe('success');
    expect(component.activeSeverity(false)).toBe('secondary');
  });
});
