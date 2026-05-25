import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { LlmConfig, RECOMMENDED_PATIENT_BEHAVIOR_CONFIG } from '../../../../core/models/admin-llm.model';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';
import { AdminLlmConfigPage } from './admin-llm-config-page';

describe('AdminLlmConfigPage', () => {
  let fixture: ComponentFixture<AdminLlmConfigPage>;
  let component: AdminLlmConfigPage;

  const baseConfig: LlmConfig = {
    provider: 'openai',
    model: 'gpt-4o-mini',
    baseUrl: 'https://api.openai.com/v1',
    enabled: true,
    apiKeyConfigured: false,
    maskedApiKey: null,
    updatedAt: null,
    patientBehavior: { ...RECOMMENDED_PATIENT_BEHAVIOR_CONFIG }
  };

  const adminLlmServiceMock = {
    getConfig: jasmine.createSpy('getConfig').and.returnValue(of(baseConfig)),
    updateConfig: jasmine.createSpy('updateConfig').and.returnValue(of(baseConfig)),
    removeApiKey: jasmine.createSpy('removeApiKey').and.returnValue(of(baseConfig)),
    testConnection: jasmine.createSpy('testConnection').and.returnValue(of({ success: true, message: 'ok' }))
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLlmConfigPage],
      providers: [
        { provide: AdminLlmService, useValue: adminLlmServiceMock },
        UserContext
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminLlmConfigPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe actualizar URL sugerida al cambiar provider', () => {
    component.form.baseUrl = 'https://api.openai.com/v1';

    component.onProviderChange('groq');

    expect(component.form.baseUrl).toBe('https://api.groq.com/openai/v1');
  });

  it('debe seleccionar modelo sugerido por defecto cuando provider cambia y modelo está vacío', () => {
    component.form.model = '';

    component.onProviderChange('groq');

    expect(component.form.model).toBe('llama-3.1-8b-instant');
  });

  it('debe seleccionar modelo sugerido por defecto de Gemini al cambiar provider', () => {
    component.form.model = '';

    component.onProviderChange('gemini');

    expect(component.form.model).toBe('gemini-2.5-flash-lite');
  });

  it('debe reemplazar modelo inválido al cambiar a Gemini', () => {
    component.form.model = 'gpt-4.1-mini';

    component.onProviderChange('gemini');

    expect(component.form.model).toBe('gemini-2.5-flash-lite');
  });

  it('debe exponer solo providers soportados por backend en selector de proveedor', () => {
    expect(component.providers).toEqual(['openai', 'groq', 'gemini', 'openrouter']);
  });

  it('debe seleccionar modelo sugerido por defecto de OpenRouter al cambiar provider', () => {
    component.form.model = '';

    component.onProviderChange('openrouter');

    expect(component.form.model).toBe('openai/gpt-4.1-mini');
  });

  it('debe mostrar modelos sugeridos de OpenRouter cuando el proveedor está activo', () => {
    component.onProviderChange('openrouter');

    expect(component.modelOptions).toEqual([
      'openai/gpt-4.1-mini',
      'google/gemini-2.0-flash-001',
      'anthropic/claude-3.5-sonnet',
      'meta-llama/llama-3.1-8b-instruct'
    ]);
  });

  it('no debe permitir guardar cuando modelo no está en catálogo conocido', () => {
    component.form.provider = 'openai';
    component.form.model = 'modelo-invalido';
    component.onSave();

    expect(component.saveError).toContain('Modelo no permitido');
    expect(adminLlmServiceMock.updateConfig).not.toHaveBeenCalled();
  });

  it('debe mantener validación de modelo para Gemini', () => {
    component.form.provider = 'gemini';
    component.form.model = 'gpt-4.1';
    component.onSave();

    expect(component.saveError).toContain('Modelo no permitido');
    expect(adminLlmServiceMock.updateConfig).not.toHaveBeenCalled();
  });

  it('no debe permitir modelo OpenRouter fuera del catálogo conocido', () => {
    component.config = { ...baseConfig, apiKeyConfigured: true };
    component.form.provider = 'openrouter';
    component.form.model = 'deepseek/deepseek-chat-v3-0324';
    component.form.apiKey = '';

    component.onSave();

    expect(component.saveError).toContain('Modelo no permitido');
    expect(adminLlmServiceMock.updateConfig).not.toHaveBeenCalled();
  });

  it('debe bloquear modelo OpenRouter vacío', () => {
    component.config = { ...baseConfig, apiKeyConfigured: true };
    component.form.provider = 'openrouter';
    component.form.model = '';

    component.onSave();

    expect(component.saveError).toContain('Debes seleccionar un modelo válido');
    expect(adminLlmServiceMock.updateConfig).not.toHaveBeenCalled();
  });

  it('debe validar API key cuando el proveedor la requiere y no existe key configurada', () => {
    component.config = { ...baseConfig, apiKeyConfigured: false };
    component.form.provider = 'openai';
    component.form.model = 'gpt-4.1-mini';
    component.form.apiKey = '';
    component.onSave();

    expect(component.saveError).toBe('Debes ingresar una API key para este proveedor.');
  });
});
