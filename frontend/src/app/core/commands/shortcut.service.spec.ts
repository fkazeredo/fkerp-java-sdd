import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ShortcutService } from './shortcut.service';

describe('ShortcutService (DL-0093)', () => {
  let service: ShortcutService;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    navigate = vi.fn();
    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: { navigate } }],
    });
    service = TestBed.inject(ShortcutService);
    service.start();
  });

  afterEach(() => service.stop());

  function press(init: KeyboardEventInit, target?: EventTarget): void {
    const event = new KeyboardEvent('keydown', { ...init, bubbles: true, cancelable: true });
    if (target) {
      Object.defineProperty(event, 'target', { value: target });
    }
    document.dispatchEvent(event);
  }

  it('opens the palette on Ctrl+K (AC4)', () => {
    expect(service.paletteOpen()).toBe(false);
    press({ key: 'k', ctrlKey: true });
    expect(service.paletteOpen()).toBe(true);
  });

  it('opens the palette on Cmd+K (AC4)', () => {
    press({ key: 'k', metaKey: true });
    expect(service.paletteOpen()).toBe(true);
  });

  it('opens the help dialog on ? (AC5)', () => {
    press({ key: '?' });
    expect(service.helpOpen()).toBe(true);
  });

  it('navigates with the leader g + nav key (AC5)', () => {
    press({ key: 'g' });
    press({ key: 'a' }); // accounts
    expect(navigate).toHaveBeenCalledWith(['/accounts']);
  });

  it('ignores single-letter shortcuts while typing in an input (AC5)', () => {
    const input = document.createElement('input');
    press({ key: 'g' }, input);
    press({ key: 'a' }, input);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('still opens the palette with Ctrl+K from within an input (AC4)', () => {
    const input = document.createElement('input');
    press({ key: 'k', ctrlKey: true }, input);
    expect(service.paletteOpen()).toBe(true);
  });
});
