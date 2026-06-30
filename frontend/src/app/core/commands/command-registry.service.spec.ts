import { TestBed } from '@angular/core/testing';
import { CommandRegistry } from './command-registry.service';

describe('CommandRegistry', () => {
  let registry: CommandRegistry;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    registry = TestBed.inject(CommandRegistry);
  });

  it('registers commands and runs by id (AC4)', () => {
    const run = vi.fn();
    registry.register([{ id: 'go', labelKey: 'nav.accounts', run }]);

    expect(registry.commands().length).toBe(1);
    registry.run('go');
    expect(run).toHaveBeenCalled();
  });

  it('exposes only commands with a hint as shortcuts (AC5)', () => {
    registry.register([
      { id: 'a', labelKey: 'nav.accounts', hint: 'g a', run: () => undefined },
      { id: 'b', labelKey: 'nav.bookings', run: () => undefined },
    ]);
    expect(registry.shortcuts().map((c) => c.id)).toEqual(['a']);
  });

  it('disposer removes exactly the registered commands', () => {
    const dispose = registry.register([{ id: 'x', labelKey: 'nav.accounts', run: () => undefined }]);
    expect(registry.commands().length).toBe(1);
    dispose();
    expect(registry.commands().length).toBe(0);
  });
});
