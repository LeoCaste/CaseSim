import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  LlmConfig,
  LlmModel,
  LlmProvider,
  PatientBehaviorConfig,
  PatientRevealStrategy,
  RECOMMENDED_PATIENT_BEHAVIOR_CONFIG,
  UpdateLlmConfigPayload
} from '../../../../core/models/admin-llm.model';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';

const PROVIDER_DEFAULTS: Record<LlmProvider, { baseUrl: string; models: LlmModel[] }> = {
  openai: {
    baseUrl: 'https://api.openai.com/v1/chat/completions',
    models: ['gpt-4o-mini', 'gpt-4.1-mini', 'gpt-4.1']
  },
  'openai-compatible': {
    baseUrl: 'https://api.openai.com/v1/chat/completions',
    models: []
  },
  anthropic: {
    baseUrl: 'https://api.anthropic.com/v1/messages',
    models: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest']
  },
  gemini: {
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/models',
    models: ['gemini-1.5-flash', 'gemini-1.5-pro']
  },
  groq: {
    baseUrl: 'https://api.groq.com/openai/v1/chat/completions',
    models: ['llama-3.1-8b-instant', 'llama-3.3-70b-versatile']
  }
};

@Component({
  selector: 'app-admin-llm-config-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-llm-config-page.html',
  styleUrl: './admin-llm-config-page.css'
})
export class AdminLlmConfigPage implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  config: LlmConfig | null = null;
  form: UpdateLlmConfigPayload = {
    provider: '',
    model: '',
    baseUrl: '',
    enabled: true,
    apiKey: '',
    patientBehavior: { ...RECOMMENDED_PATIENT_BEHAVIOR_CONFIG }
  };

  isLoading = false;
  isSaving = false;
  isDeletingApiKey = false;
  isTestingConnection = false;
  loadError = '';
  saveMessage = '';
  saveError = '';
  testFeedback = '';
  testFeedbackStatus: 'success' | 'error' | null = null;

  readonly providers: LlmProvider[] = ['openai', 'openai-compatible', 'anthropic', 'gemini', 'groq'];
  readonly revealStrategies: PatientRevealStrategy[] = ['PROGRESSIVE', 'DIRECT', 'RESTRICTIVE'];
  readonly basePromptMaxLength = 4000;
  readonly additionalRulesMaxLength = 3000;
  readonly noInformationReplyMaxLength = 500;
  readonly maxTokensMin = 64;
  readonly maxTokensMax = 1024;
  readonly modelsByProvider: Record<LlmProvider, LlmModel[]> = Object.fromEntries(
    Object.entries(PROVIDER_DEFAULTS).map(([provider, defaults]) => [provider, defaults.models])
  ) as Record<LlmProvider, LlmModel[]>;

  constructor(
    private adminLlmService: AdminLlmService,
    private userContext: UserContext
  ) {
    this.userContext.setRole('admin');
  }

  ngOnInit(): void {
    this.loadConfig();
  }

  onSave(): void {
    if (this.isSaving || this.isDeletingApiKey || this.isLoading || this.isTestingConnection) {
      return;
    }

    this.saveMessage = '';
    this.saveError = '';

    if (!this.form.provider.trim() || !this.form.model.trim() || !this.form.baseUrl.trim()) {
      this.saveError = 'Completa proveedor y modelo para guardar la configuración.';
      return;
    }

    const modelValidationError = this.validateModel(this.form.provider, this.form.model);
    if (modelValidationError) {
      this.saveError = modelValidationError;
      return;
    }

    const validationError = this.validatePatientBehavior(this.form.patientBehavior);
    if (validationError) {
      this.saveError = validationError;
      return;
    }

    this.isSaving = true;
    this.triggerViewUpdate();
    const payload = this.buildSanitizedPayload(this.form);
    this.adminLlmService
      .updateConfig(payload)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSaving = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (updatedConfig) => {
          this.config = updatedConfig;
          const provider = this.normalizeProvider(updatedConfig.provider);
          const model = this.normalizeModel(provider, updatedConfig.model);
          this.form = {
            provider,
            model,
            baseUrl: this.resolveBaseUrl(updatedConfig.baseUrl, provider),
            enabled: updatedConfig.enabled,
            apiKey: '',
            patientBehavior: this.clonePatientBehavior(updatedConfig.patientBehavior)
          };
          this.saveMessage = 'Configuración guardada correctamente.';
          this.triggerViewUpdate();
        },
        error: (error: unknown) => {
          this.saveError = error instanceof Error ? error.message : 'No fue posible actualizar la configuración LLM.';
          this.triggerViewUpdate();
        }
      });
  }

  onRestoreRecommended(): void {
    this.form.patientBehavior = this.clonePatientBehavior(RECOMMENDED_PATIENT_BEHAVIOR_CONFIG);
    this.saveMessage = 'Valores recomendados cargados. Guarda para aplicar.';
    this.saveError = '';
  }

  onTestConnection(): void {
    if (this.isTestingConnection || this.isSaving || this.isDeletingApiKey || this.isLoading) {
      return;
    }

    this.testFeedback = '';
    this.testFeedbackStatus = null;
    this.isTestingConnection = true;
    this.triggerViewUpdate();

    this.adminLlmService
      .testConnection()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isTestingConnection = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (response) => {
          this.testFeedback = response.message || (response.success ? 'Conexión exitosa' : 'No se pudo conectar con el proveedor');
          this.testFeedbackStatus = response.success ? 'success' : 'error';
          this.triggerViewUpdate();
        },
        error: () => {
          this.testFeedback = 'No se pudo conectar con el proveedor';
          this.testFeedbackStatus = 'error';
          this.triggerViewUpdate();
        }
      });
  }

  onRemoveApiKey(): void {
    if (!this.config?.apiKeyConfigured || this.isDeletingApiKey || this.isSaving || this.isTestingConnection || this.isLoading) {
      return;
    }

    const confirmed = window.confirm(
      '¿Eliminar API key actual? Esta acción desactiva el acceso al proveedor hasta configurar una nueva key.'
    );

    if (!confirmed) {
      return;
    }

    this.saveMessage = '';
    this.saveError = '';
    this.testFeedback = '';
    this.testFeedbackStatus = null;
    this.isDeletingApiKey = true;
    this.triggerViewUpdate();

    const payload: UpdateLlmConfigPayload = {
      ...this.buildSanitizedPayload(this.form),
      apiKey: ''
    };

    this.adminLlmService
      .removeApiKey(payload)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isDeletingApiKey = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (updatedConfig) => {
          this.config = {
            ...updatedConfig,
            apiKeyConfigured: false,
            maskedApiKey: null
          };
          this.form.apiKey = '';
          this.saveMessage = 'API key eliminada correctamente.';
          this.saveError = '';
          this.triggerViewUpdate();
        },
        error: (error: unknown) => {
          this.saveError = error instanceof Error ? error.message : 'No fue posible eliminar la API key.';
          this.saveMessage = '';
          this.triggerViewUpdate();
        }
      });
  }

  onProviderChange(providerInput: string): void {
    const provider = this.normalizeProvider(providerInput);
    const previousProvider = this.normalizeProvider(this.form.provider);
    this.form.provider = provider;

    const validModels = this.modelsByProvider[provider] ?? [];
    if (validModels.length > 0 && !validModels.includes(this.form.model as LlmModel)) {
      this.form.model = validModels[0];
    }

    if (provider === 'openai-compatible' && previousProvider !== 'openai-compatible' && !this.form.model.trim()) {
      this.form.model = 'gpt-4o-mini';
    }

    const previousDefault = PROVIDER_DEFAULTS[previousProvider]?.baseUrl;
    if (!this.form.baseUrl.trim() || this.form.baseUrl.trim() === previousDefault) {
      this.form.baseUrl = this.resolveBaseUrl(null, provider);
    }
  }

  get modelOptions(): LlmModel[] {
    const provider = this.normalizeProvider(this.form.provider);
    const options = [...(this.modelsByProvider[provider] ?? [])];
    const currentModel = this.form.model?.trim();

    if (currentModel && !options.includes(currentModel as LlmModel)) {
      options.unshift(currentModel as LlmModel);
    }

    return options;
  }

  get isModelCustom(): boolean {
    return this.normalizeProvider(this.form.provider) === 'openai-compatible';
  }

  get enabledStatusLabel(): string {
    return this.config?.enabled ? 'Activado' : 'Desactivado';
  }

  get apiKeyConfiguredLabel(): string {
    return this.config?.apiKeyConfigured ? 'Sí' : 'No';
  }

  get hasApiKeyMask(): boolean {
    return Boolean(this.config?.maskedApiKey?.trim());
  }

  private loadConfig(): void {
    this.isLoading = true;
    this.loadError = '';
    this.saveMessage = '';
    this.saveError = '';
    this.testFeedback = '';
    this.testFeedbackStatus = null;
    this.triggerViewUpdate();

    this.adminLlmService
      .getConfig()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: (config) => {
          this.config = config;
          const provider = this.normalizeProvider(config.provider);
          const model = this.normalizeModel(provider, config.model);
          this.form = {
            provider,
            model,
            baseUrl: this.resolveBaseUrl(config.baseUrl, provider),
            enabled: config.enabled,
            apiKey: '',
            patientBehavior: this.clonePatientBehavior(config.patientBehavior)
          };
          this.triggerViewUpdate();
        },
        error: (error: unknown) => {
          this.config = null;
          this.loadError = error instanceof Error ? error.message : 'No fue posible cargar la configuración actual.';
          this.triggerViewUpdate();
        }
      });
  }

  private triggerViewUpdate(): void {
    if (this.destroyRef.destroyed) {
      return;
    }

    this.cdr.detectChanges();
  }

  private normalizeProvider(provider: string): LlmProvider {
    if (provider === 'openai-compatible') {
      return 'openai-compatible';
    }

    if (provider === 'anthropic') {
      return 'anthropic';
    }

    if (provider === 'gemini') {
      return 'gemini';
    }

    if (provider === 'groq') {
      return 'groq';
    }

    return 'openai';
  }

  private normalizeModel(provider: LlmProvider, model: string): LlmModel {
    const options = this.modelsByProvider[provider] ?? [];
    const trimmedModel = model?.trim();

    if (provider === 'openai-compatible') {
      return (trimmedModel || 'gpt-4o-mini') as LlmModel;
    }

    if (!options.length) {
      return (trimmedModel || 'gpt-4o-mini') as LlmModel;
    }

    if (!trimmedModel) {
      return options[0];
    }

    return trimmedModel as LlmModel;
  }

  private resolveBaseUrl(baseUrl: string | null | undefined, provider: string): string {
    const trimmedBaseUrl = baseUrl?.trim();
    if (trimmedBaseUrl) {
      return trimmedBaseUrl;
    }

    const normalizedProvider = this.normalizeProvider(provider);
    return PROVIDER_DEFAULTS[normalizedProvider]?.baseUrl ?? PROVIDER_DEFAULTS.openai.baseUrl;
  }

  private buildSanitizedPayload(form: UpdateLlmConfigPayload): UpdateLlmConfigPayload {
    const behavior = this.clonePatientBehavior(form.patientBehavior);
    behavior.basePrompt = behavior.basePrompt?.trim() ?? '';
    behavior.additionalRules = behavior.additionalRules?.trim() ?? '';
    behavior.noInformationReply = behavior.noInformationReply?.trim() ?? '';
    behavior.temperature = Number(behavior.temperature);
    behavior.maxTokens = Number(behavior.maxTokens);
    behavior.maxHistoryMessages = Number(behavior.maxHistoryMessages);

    return {
      provider: this.normalizeProvider(form.provider),
      model: form.model?.trim(),
      baseUrl: form.baseUrl?.trim(),
      enabled: form.enabled,
      apiKey: form.apiKey,
      patientBehavior: behavior
    };
  }

  private validatePatientBehavior(behavior: PatientBehaviorConfig): string | null {
    if (behavior.temperature < 0 || behavior.temperature > 2) {
      return 'La temperatura debe estar entre 0.0 y 2.0.';
    }

    if (behavior.maxTokens < this.maxTokensMin || behavior.maxTokens > this.maxTokensMax) {
      return `Max tokens debe estar entre ${this.maxTokensMin} y ${this.maxTokensMax}.`;
    }

    if (behavior.maxHistoryMessages < 1) {
      return 'Historial máximo debe ser mayor o igual a 1.';
    }

    if (behavior.maxHistoryMessages > 30) {
      return 'Historial máximo debe ser menor o igual a 30.';
    }

    return null;
  }

  private validateModel(providerInput: string, modelInput: string): string | null {
    const provider = this.normalizeProvider(providerInput);
    const model = modelInput?.trim();

    if (!model) {
      return 'Debes ingresar un modelo válido.';
    }

    if (provider === 'openai-compatible') {
      return /^[\w./:-]{2,120}$/.test(model)
        ? null
        : 'El modelo contiene caracteres inválidos. Usa letras, números y separadores habituales (./:-_).';
    }

    const validModels = this.modelsByProvider[provider] ?? [];
    if (validModels.length > 0 && !validModels.includes(model as LlmModel)) {
      return `Modelo no permitido para ${provider}. Selecciona uno de la lista.`;
    }

    return null;
  }

  onRestoreProviderBaseUrl(): void {
    this.form.baseUrl = this.resolveBaseUrl('', this.form.provider);
  }

  private clonePatientBehavior(source: PatientBehaviorConfig): PatientBehaviorConfig {
    return {
      basePrompt: source?.basePrompt ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.basePrompt,
      additionalRules: source?.additionalRules ?? '',
      noInformationReply: source?.noInformationReply ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.noInformationReply,
      revealStrategy: source?.revealStrategy ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.revealStrategy,
      maxHistoryMessages: source?.maxHistoryMessages ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.maxHistoryMessages,
      temperature: source?.temperature ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.temperature,
      maxTokens: source?.maxTokens ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.maxTokens,
      safetyFilterEnabled: source?.safetyFilterEnabled ?? RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.safetyFilterEnabled
    };
  }
}
