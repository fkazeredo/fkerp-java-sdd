import { runInInjectionContext } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { FormLeaveGuard, canDeactivateGuard } from './can-deactivate.guard';

function invoke(component: FormLeaveGuard): boolean {
  const injector = TestBed.inject(EnvironmentInjector);
  return runInInjectionContext(injector, () =>
    canDeactivateGuard(
      component,
      {} as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
      {} as RouterStateSnapshot,
    ),
  ) as boolean;
}

describe('canDeactivateGuard (AC8/BR9)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: TranslateService, useValue: { instant: (k: string) => k } }],
    });
  });

  it('allows leaving when the form is clean', () => {
    expect(invoke({ isDirty: () => false })).toBe(true);
  });

  it('asks for confirmation when the form is dirty and proceeds on accept', () => {
    const original = window.confirm;
    window.confirm = vi.fn(() => true);
    try {
      expect(invoke({ isDirty: () => true })).toBe(true);
      expect(window.confirm).toHaveBeenCalled();
    } finally {
      window.confirm = original;
    }
  });

  it('blocks leaving when the user cancels the confirmation', () => {
    const original = window.confirm;
    window.confirm = vi.fn(() => false);
    try {
      expect(invoke({ isDirty: () => true })).toBe(false);
    } finally {
      window.confirm = original;
    }
  });
});
