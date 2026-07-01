import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CadastroLabelPipe } from './cadastro-label.pipe';
import { CadastroLabelService } from './cadastro-label.service';

describe('CadastroLabelPipe', () => {
  let http: HttpTestingController;
  let pipe: CadastroLabelPipe;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CadastroLabelService,
        CadastroLabelPipe,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    pipe = TestBed.inject(CadastroLabelPipe);
  });

  afterEach(() => http.verify());

  it('triggers the load and returns the code until it resolves, then the label', () => {
    // First transform kicks off the fetch and falls back to the code.
    expect(pipe.transform('REVENUE', 'GOAL_METRIC')).toBe('REVENUE');

    http
      .expectOne((r) => r.params.get('type') === 'GOAL_METRIC')
      .flush([
        { id: '1', type: 'GOAL_METRIC', code: 'REVENUE', label: 'Receita (spread BRL)', active: true, sortOrder: 20, createdAt: '' },
      ]);

    // After the fetch resolves, the pipe renders the label.
    expect(pipe.transform('REVENUE', 'GOAL_METRIC')).toBe('Receita (spread BRL)');
  });
});
