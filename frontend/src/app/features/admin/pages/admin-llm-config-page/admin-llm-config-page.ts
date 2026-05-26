import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  LlmConfig,
  LlmModel,
  LlmProvider,
  LlmTestConnectionResult,
  PatientBehaviorConfig,
  PatientRevealStrategy,
  RECOMMENDED_PATIENT_BEHAVIOR_CONFIG,
  UpdateLlmConfigPayload
} from '../../../../core/models/admin-llm.model';
import { ADMIN_LLM_ACTIVE_PROVIDERS, LLM_PROVIDER_CATALOG } from '../../../../core/models/llm-provider-catalog';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';

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
  testFeedbackStatusCode: number | null = null;
  testFeedbackErrorCode = '';
  testFeedbackTraceId = '';
  testFeedbackRetryable = false;
  readonly providers: LlmProvider[] = ADMIN_LLM_ACTIVE_PROVIDERS;
  readonly revealStrategies: PatientRevealStrategy[] = ['PROGRESSIVE', 'DIRECT', 'RESTRICTIVE'];
  readonly basePromptMaxLength = 4000;
  readonly additionalRulesMaxLength = 3000;
  readonly noInformationReplyMaxLength = 500;
  readonly maxTokensMin = 64;
  readonly maxTokensMax = 1024;
  readonly genericModelPattern = /^[^\s]+$/;
  readonly suggestedModelsByProvider: Record<LlmProvider, LlmModel[]> = Object.fromEntries(
    Object.entries(LLM_PROVIDER_CATALOG).map(([provider, defaults]) => [provider, defaults.suggestedModels])
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

    if (!this.form.provider.trim()) {
      this.saveError = 'Debes seleccionar un proveedor.';
      return;
    }

    if (!this.form.model.trim()) {
      this.saveError = 'Debes seleccionar un modelo válido.';
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

    const trimmedApiKey = this.form.apiKey?.trim() ?? '';
    if (this.isApiKeyRequired && !this.config?.apiKeyConfigured && !trimmedApiKey) {
      this.saveError = 'Debes ingresar una API key para este proveedor.';
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
          const resolvedBaseUrl = this.resolveBaseUrl(updatedConfig.baseUrl, provider);
          this.form = {
            provider,
            model,
            baseUrl: resolvedBaseUrl,
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
    this.testFeedbackStatusCode = null;
    this.testFeedbackErrorCode = '';
    this.testFeedbackTraceId = '';
    this.testFeedbackRetryable = false;

    const providerModelValidationError = this.validateProviderModelPair(this.form.provider, this.form.model);
    if (providerModelValidationError) {
      this.testFeedback = providerModelValidationError;
      this.testFeedbackStatus = 'error';
      return;
    }

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
          this.applyTestConnectionFeedback(response);
          this.triggerViewUpdate();
        },
        error: () => {
          this.testFeedback = 'No se pudo conectar con el proveedor';
          this.testFeedbackStatus = 'error';
          this.testFeedbackStatusCode = null;
          this.testFeedbackErrorCode = '';
          this.testFeedbackTraceId = '';
          this.testFeedbackRetryable = false;
          this.triggerViewUpdate();
        }
      });
  }

  onRemoveApiKey(): void {
    if (!this.config?.apiKeyConfigured || this.isDeletingApiKey || this.isSaving || this.isTestingConnection || this.isLoading) {
      return;
    }

    const confirmed = window.confirm(
      '¿Eliminar API key actual? Esta acción quitará la credencial guardada para el proveedor activo.'
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

    this.adminLlmService
      .removeApiKey()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isDeletingApiKey = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: () => {
          this.saveMessage = '';
          this.saveError = '';
          this.form.apiKey = '';
          this.reloadConfigAfterApiKeyRemoval();
        },
        error: (error: unknown) => {
          this.saveError = error instanceof Error ? error.message : 'No fue posible eliminar la API key.';
          this.saveMessage = '';
          this.triggerViewUpdate();
        }
      });
  }

  private reloadConfigAfterApiKeyRemoval(): void {
    this.isLoading = true;
    this.loadError = '';
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
          const resolvedBaseUrl = this.resolveBaseUrl(config.baseUrl, provider);
          this.form = {
            provider,
            model,
            baseUrl: resolvedBaseUrl,
            enabled: config.enabled,
            apiKey: '',
            patientBehavior: this.clonePatientBehavior(config.patientBehavior)
          };
          if (!this.form.model?.trim()) {
            this.form.model = this.getProviderDefaultModel(provider);
          }

          if (config.apiKeyConfigured) {
            this.saveMessage = 'Solicitud aplicada, pero el backend mantiene API key configurada.';
            this.saveError = 'La credencial no se eliminó completamente. Verifica estado en backend y vuelve a intentar.';
          } else {
            this.saveMessage = 'API key eliminada correctamente.';
            this.saveError = '';
          }

          this.triggerViewUpdate();
        },
        error: (error: unknown) => {
          this.loadError = error instanceof Error ? error.message : 'No fue posible recargar la configuración actual.';
          this.saveError = 'API key eliminada, pero no fue posible recargar la configuración actual.';
          this.triggerViewUpdate();
        }
      });
  }

  onProviderChange(providerInput: string): void {
    const provider = this.normalizeProvider(providerInput);
    this.form.provider = provider;

    const selectedModel = this.form.model?.trim();
    const isSelectedModelAllowed = !!selectedModel && this.modelOptionsByProvider(provider).includes(selectedModel as LlmModel);
    if (!selectedModel || !isSelectedModelAllowed) {
      this.form.model = this.getProviderDefaultModel(provider);
    }

    this.form.baseUrl = this.resolveBaseUrl(null, provider);
  }

  getProviderLabel(providerInput: string): string {
    const provider = this.normalizeProvider(providerInput);
    return LLM_PROVIDER_CATALOG[provider]?.label ?? provider;
  }

  get modelOptions(): LlmModel[] {
    const provider = this.normalizeProvider(this.form.provider);
    return [...(this.suggestedModelsByProvider[provider] ?? [])];
  }

  get isApiKeyRequired(): boolean {
    const provider = this.normalizeProvider(this.form.provider);
    return LLM_PROVIDER_CATALOG[provider]?.requiresApiKey ?? true;
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

  get hasTestFeedbackTraceability(): boolean {
    return !!this.testFeedbackStatusCode || !!this.testFeedbackErrorCode || !!this.testFeedbackTraceId;
  }

  private loadConfig(): void {
    this.isLoading = true;
    this.loadError = '';
    this.saveMessage = '';
    this.saveError = '';
    this.testFeedback = '';
    this.testFeedbackStatus = null;
    this.testFeedbackStatusCode = null;
    this.testFeedbackErrorCode = '';
    this.testFeedbackTraceId = '';
    this.testFeedbackRetryable = false;
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
          const resolvedBaseUrl = this.resolveBaseUrl(config.baseUrl, provider);
          this.form = {
            provider,
            model,
            baseUrl: resolvedBaseUrl,
            enabled: config.enabled,
            apiKey: '',
            patientBehavior: this.clonePatientBehavior(config.patientBehavior)
          };
          if (!this.form.model?.trim()) {
            this.form.model = this.getProviderDefaultModel(provider);
          }
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
    if (provider === 'anthropic') {
      return 'anthropic';
    }

    if (provider === 'openrouter') {
      return 'openrouter';
    }

    if (provider === 'gemini') {
      return 'gemini';
    }

    if (provider === 'groq') {
      return 'groq';
    }

    if (provider === 'openai-compatible') {
      return 'openai-compatible';
    }

    return 'openai';
  }

  private normalizeModel(provider: LlmProvider, model: string): LlmModel {
    const options = this.modelOptionsByProvider(provider);
    const trimmedModel = model?.trim();
    const defaultModel = this.getProviderDefaultModel(provider);

    if (!options.length) {
      return (trimmedModel || defaultModel) as LlmModel;
    }

    if (!trimmedModel) {
      return defaultModel;
    }

    if (!options.includes(trimmedModel as LlmModel)) {
      return defaultModel;
    }

    return trimmedModel as LlmModel;
  }

  private resolveBaseUrl(baseUrl: string | null | undefined, provider: string): string {
    const trimmedBaseUrl = baseUrl?.trim();
    if (trimmedBaseUrl) {
      return trimmedBaseUrl;
    }

    const normalizedProvider = this.normalizeProvider(provider);
    return LLM_PROVIDER_CATALOG[normalizedProvider]?.defaultBaseUrl ?? LLM_PROVIDER_CATALOG.openai.defaultBaseUrl;
  }

  private buildSanitizedPayload(form: UpdateLlmConfigPayload): UpdateLlmConfigPayload {
    const normalizedProvider = this.normalizeProvider(form.provider);
    const behavior = this.clonePatientBehavior(form.patientBehavior);
    behavior.basePrompt = behavior.basePrompt?.trim() ?? '';
    behavior.additionalRules = behavior.additionalRules?.trim() ?? '';
    behavior.noInformationReply = behavior.noInformationReply?.trim() ?? '';
    behavior.temperature = Number(behavior.temperature);
    behavior.maxTokens = Number(behavior.maxTokens);
    behavior.maxHistoryMessages = Number(behavior.maxHistoryMessages);

    return {
      provider: normalizedProvider,
      model: form.model?.trim(),
      baseUrl: this.resolveBaseUrl(form.baseUrl, normalizedProvider),
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
    const validModels = this.modelOptionsByProvider(provider);

    if (!validModels.length) {
      return `No hay catálogo de modelos disponible para ${this.getProviderLabel(provider)}. Intenta más tarde o contacta soporte.`;
    }

    if (!model) {
      return 'Debes seleccionar un modelo válido.';
    }

    if (!this.genericModelPattern.test(model)) {
      return 'Modelo inválido. Usa un identificador sin espacios (ej: gpt-4.1-mini).';
    }

    const providerModelValidationError = this.validateProviderModelPair(provider, model);
    if (providerModelValidationError) {
      return providerModelValidationError;
    }

    if (validModels.length > 0 && !validModels.includes(model as LlmModel)) {
      return `Modelo no permitido para ${this.getProviderLabel(provider)}. Selecciona un modelo sugerido.`;
    }

    return null;
  }

  private getProviderDefaultModel(provider: LlmProvider): LlmModel {
    return LLM_PROVIDER_CATALOG[provider]?.defaultModel ?? LLM_PROVIDER_CATALOG.openai.defaultModel;
  }

  onModelOptionChange(value: string): void {
    this.form.model = value?.trim() ?? '';
    this.saveError = '';
  }

  private modelOptionsByProvider(provider: LlmProvider): LlmModel[] {
    return [...(this.suggestedModelsByProvider[provider] ?? [])];
  }

  private validateProviderModelPair(providerInput: string, modelInput: string): string | null {
    const provider = this.normalizeProvider(providerInput);
    const model = modelInput?.trim() ?? '';

    if (provider === 'anthropic' && model.includes('/')) {
      return 'Este modelo parece ser de OpenRouter. Para Anthropic directo usa un modelo sin prefijo.';
    }

    return null;
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

  private applyTestConnectionFeedback(response: LlmTestConnectionResult): void {
    this.testFeedback = response.message || (response.success ? 'Conexión exitosa' : 'No se pudo conectar con el proveedor');
    this.testFeedbackStatus = response.success ? 'success' : 'error';
    this.testFeedbackStatusCode = response.statusCode ?? null;
    this.testFeedbackErrorCode = response.errorCode?.trim() ?? '';
    this.testFeedbackTraceId = response.traceId?.trim() ?? '';
    this.testFeedbackRetryable = !!response.retryable;
  }
}
