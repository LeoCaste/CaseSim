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

const OPENAI_BASE_URL = 'https://api.openai.com/v1/chat/completions';

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
  isTestingConnection = false;
  loadError = '';
  saveMessage = '';
  saveError = '';
  testFeedback = '';
  testFeedbackStatus: 'success' | 'error' | null = null;

  readonly providers: LlmProvider[] = ['openai'];
  readonly revealStrategies: PatientRevealStrategy[] = ['PROGRESSIVE', 'DIRECT', 'RESTRICTIVE'];
  readonly basePromptMaxLength = 4000;
  readonly additionalRulesMaxLength = 3000;
  readonly noInformationReplyMaxLength = 500;
  readonly maxTokensMin = 64;
  readonly maxTokensMax = 1024;
  readonly modelsByProvider: Record<LlmProvider, LlmModel[]> = {
    openai: ['gpt-4o-mini', 'gpt-4.1-mini', 'gpt-4.1']
  };

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
    this.saveMessage = '';
    this.saveError = '';

    if (!this.form.provider.trim() || !this.form.model.trim() || !this.form.baseUrl.trim()) {
      this.saveError = 'Completa proveedor y modelo para guardar la configuración.';
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
        error: () => {
          this.saveError = 'No fue posible actualizar la configuración LLM.';
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

  onProviderChange(provider: string): void {
    this.form.provider = provider;
    const validModels = this.modelsByProvider.openai;
    if (!validModels.includes(this.form.model as LlmModel)) {
      this.form.model = validModels[0];
    }
    this.form.baseUrl = this.resolveBaseUrl(this.form.baseUrl, provider);
  }

  get modelOptions(): LlmModel[] {
    if (this.form.provider === 'openai') {
      return this.modelsByProvider.openai;
    }

    return this.modelsByProvider.openai;
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
        error: () => {
          this.config = null;
          this.loadError = 'No fue posible cargar la configuración actual.';
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
    return provider === 'openai' ? 'openai' : 'openai';
  }

  private normalizeModel(provider: LlmProvider, model: string): LlmModel {
    const options = this.modelsByProvider[provider];
    return options.includes(model as LlmModel) ? (model as LlmModel) : options[0];
  }

  private resolveBaseUrl(baseUrl: string | null | undefined, provider: string): string {
    const trimmedBaseUrl = baseUrl?.trim();
    if (trimmedBaseUrl) {
      return trimmedBaseUrl;
    }

    if (provider === 'openai') {
      return OPENAI_BASE_URL;
    }

    return OPENAI_BASE_URL;
  }

  private buildSanitizedPayload(form: UpdateLlmConfigPayload): UpdateLlmConfigPayload {
    const behavior = this.clonePatientBehavior(form.patientBehavior);
    behavior.basePrompt = behavior.basePrompt?.trim() || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.basePrompt;
    behavior.additionalRules = behavior.additionalRules?.trim() || '';
    behavior.noInformationReply =
      behavior.noInformationReply?.trim() || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.noInformationReply;
    behavior.temperature = Number(behavior.temperature);
    behavior.maxTokens = Number(behavior.maxTokens);
    behavior.maxHistoryMessages = Number(behavior.maxHistoryMessages);

    return {
      provider: form.provider,
      model: form.model,
      baseUrl: form.baseUrl,
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
