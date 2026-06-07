import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

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
    getConfig: vi.fn().mockReturnValue(of(baseConfig)),
    updateConfig: vi.fn().mockReturnValue(of(baseConfig)),
    removeApiKey: vi.fn().mockReturnValue(of(baseConfig)),
    testConnection: vi.fn().mockReturnValue(of({ success: true, message: 'ok' }))
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

  it('debe mostrar la API key solo enmascarada y el input como password', () => {
    component.config = {
      ...baseConfig,
      apiKeyConfigured: true,
      maskedApiKey: '************1234'
    };
    component.form.apiKey = 'super-secret-key';

    fixture.detectChanges();

    const apiKeyInput = fixture.nativeElement.querySelector('#apiKey') as HTMLInputElement;

    expect(apiKeyInput.type).toBe('password');
    expect(fixture.nativeElement.textContent).toContain('API key actual: ************1234');
    expect(fixture.nativeElement.textContent).not.toContain('super-secret-key');
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
      'anthropic/claude-sonnet-4',
      'meta-llama/llama-3.1-8b-instruct'
    ]);
  });

  it('debe bloquear test de conexión para Anthropic directo con modelo prefijado', () => {
    component.form.provider = 'anthropic';
    component.form.model = 'anthropic/claude-3.7-sonnet';

    component.onTestConnection();

    expect(component.testFeedbackStatus).toBe('error');
    expect(component.testFeedback).toBe(
      'Este modelo parece ser de OpenRouter. Para Anthropic directo usa un modelo sin prefijo.'
    );
    expect(adminLlmServiceMock.testConnection).not.toHaveBeenCalled();
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

  it('debe mostrar mensaje de error simple al fallar test de conexión', () => {
    adminLlmServiceMock.testConnection.mockReturnValue(
      of({
        success: false,
        message: 'Credenciales inválidas para el proveedor.',
        statusCode: 401,
        errorCode: 'AUTH_INVALID',
        traceId: 'trace-123',
        retryable: false
      })
    );

    component.onTestConnection();

    expect(component.testFeedbackStatus).toBe('error');
    expect(component.testFeedback).toContain('Credenciales inválidas');
    expect(component.isTestingConnection).toBeFalsy();
  });

  it('debe apagar loading de test cuando servicio lanza error', () => {
    adminLlmServiceMock.testConnection.mockReturnValue(throwError(() => new Error('network')));

    component.onTestConnection();

    expect(component.testFeedbackStatus).toBe('error');
    expect(component.testFeedback).toBe('No se pudo conectar con el proveedor');
    expect(component.isTestingConnection).toBeFalsy();
  });
});
