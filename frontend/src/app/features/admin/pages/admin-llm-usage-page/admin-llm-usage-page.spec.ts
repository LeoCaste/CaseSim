import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { LlmUsageDailyMetric, LlmUsageSummary } from '../../../../core/models/admin-llm.model';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';
import { AdminLlmUsagePage } from './admin-llm-usage-page';

describe('AdminLlmUsagePage', () => {
  let fixture: ComponentFixture<AdminLlmUsagePage>;
  let component: AdminLlmUsagePage;

  const usage: LlmUsageDailyMetric[] = [
    {
      date: '2026-05-01',
      tokensInput: 100,
      tokensOutput: 50,
      calls: 2,
      avgLatencyMs: 800,
      provider: 'openai',
      model: 'gpt-4o-mini',
      tokenEstimated: true
    }
  ];

  const summary: LlmUsageSummary = {
    totalCalls: 2,
    totalTokens: 150,
    avgLatencyMs: 800,
    fallbackCount: 0,
    errorCount: 0,
    estimatedCostUsd: 1,
    estimatedCostClp: 900,
    usdToClpRate: 900
  };

  const adminLlmServiceMock = {
    getUsageSnapshot: vi.fn().mockReturnValue(of({ usage, summary }))
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLlmUsagePage],
      providers: [
        { provide: AdminLlmService, useValue: adminLlmServiceMock },
        UserContext
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminLlmUsagePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('oculta filtro de modelo cuando no hay proveedor seleccionado', () => {
    component.filterForm.provider = '';
    component.onProviderFilterChange();
    fixture.detectChanges();

    const modelSelect = fixture.nativeElement.querySelector('#model');
    const modelLabel = fixture.nativeElement.querySelector('label[for="model"]');

    expect(modelLabel).toBeNull();
    expect(modelSelect).toBeNull();
  });

  it('muestra filtro de modelo cuando hay proveedor seleccionado', () => {
    component.filterForm.provider = 'openai';
    component.onProviderFilterChange();
    fixture.detectChanges();

    const modelSelect = fixture.nativeElement.querySelector('#model');
    const modelLabel = fixture.nativeElement.querySelector('label[for="model"]');

    expect(modelLabel).not.toBeNull();
    expect(modelSelect).not.toBeNull();
  });
});
