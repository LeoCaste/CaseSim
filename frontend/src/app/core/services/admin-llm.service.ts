import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, forkJoin, map, Observable, of, throwError, timeout } from 'rxjs';

import {
  LlmConfig,
  PatientBehaviorConfig,
  RECOMMENDED_PATIENT_BEHAVIOR_CONFIG,
  LlmTestConnectionResult,
  LlmUsageDailyMetric,
  LlmUsageFilters,
  LlmUsageSummary,
  UpdateLlmConfigPayload
} from '../models/admin-llm.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminLlmService {
  private readonly requestTimeoutMs = 8000;
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private mockUsageTick = 0;

  private mockConfig: LlmConfig = {
    provider: 'openai',
    model: 'gpt-4o-mini',
    baseUrl: 'https://api.openai.com/v1/chat/completions',
    enabled: true,
    apiKeyConfigured: true,
    maskedApiKey: '************7890',
    updatedAt: new Date().toISOString(),
    patientBehavior: { ...RECOMMENDED_PATIENT_BEHAVIOR_CONFIG }
  };

  private mockUsage: LlmUsageDailyMetric[] = [
    {
      date: '2026-04-25',
      tokensInput: 25100,
      tokensOutput: 18320,
      calls: 182,
      avgLatencyMs: 812
    },
    {
      date: '2026-04-26',
      tokensInput: 22840,
      tokensOutput: 16970,
      calls: 170,
      avgLatencyMs: 776
    }
  ];

  constructor(private http: HttpClient) {}

  getConfig(): Observable<LlmConfig> {
    if (!environment.useMocks) {
      return this.http
        .get<BackendLlmConfigResponse>(`${this.apiBaseUrl}/admin/llm/config`)
        .pipe(
          timeout(this.requestTimeoutMs),
          map((response) => this.mapConfigResponse(response)),
          catchError(() => throwError(() => new Error('No fue posible cargar configuración LLM.')))
        );
    }

    return of(this.mockConfig);
  }

  updateConfig(payload: UpdateLlmConfigPayload): Observable<LlmConfig> {
    if (!environment.useMocks) {
      return this.http
        .put<BackendLlmConfigResponse>(`${this.apiBaseUrl}/admin/llm/config`, this.mapUpdatePayload(payload))
        .pipe(
          timeout(this.requestTimeoutMs),
          map((response) => this.mapConfigResponse(response)),
          catchError(() => throwError(() => new Error('No fue posible actualizar configuración LLM.')))
        );
    }

    const hasNewApiKey = Boolean(payload.apiKey && payload.apiKey.trim().length > 0);
    this.mockConfig = {
      ...this.mockConfig,
      provider: payload.provider,
      model: payload.model,
      baseUrl: payload.baseUrl,
      enabled: payload.enabled,
      apiKeyConfigured: hasNewApiKey ? true : this.mockConfig.apiKeyConfigured,
      maskedApiKey: hasNewApiKey ? '************mock' : this.mockConfig.maskedApiKey,
      updatedAt: new Date().toISOString(),
      patientBehavior: {
        ...payload.patientBehavior
      }
    };

    return of(this.mockConfig);
  }

  testConnection(): Observable<LlmTestConnectionResult> {
    if (!environment.useMocks) {
      return this.http.post<BackendTestConnectionResponse>(`${this.apiBaseUrl}/admin/llm/test-connection`, {}).pipe(
        timeout(this.requestTimeoutMs),
        map((response) => ({
          success: response.success,
          message: response.success ? 'Conexión exitosa' : 'No se pudo conectar con el proveedor'
        })),
        catchError(() =>
          of({
            success: false,
            message: 'No se pudo conectar con el proveedor'
          })
        )
      );
    }

    if (!this.mockConfig.enabled || !this.mockConfig.apiKeyConfigured) {
      return of({
        success: false,
        message: 'No se pudo conectar con el proveedor'
      });
    }

    return of({
      success: true,
      message: 'Conexión exitosa'
    });
  }

  getUsage(filters?: LlmUsageFilters): Observable<LlmUsageDailyMetric[]> {
    if (!environment.useMocks) {
      return this.http.get<BackendLlmUsageDailyResponse[]>(`${this.apiBaseUrl}/admin/llm/usage`, {
        params: this.buildUsageParams(filters)
      }).pipe(
        timeout(this.requestTimeoutMs),
        map((response) => response.map((item) => this.mapUsageItem(item))),
        catchError(() => throwError(() => new Error('No fue posible cargar uso LLM.')))
      );
    }

    return of(this.getMockUsageSnapshot(filters));
  }

  getSummary(filters?: LlmUsageFilters): Observable<LlmUsageSummary> {
    if (!environment.useMocks) {
      return this.http.get<BackendLlmSummaryResponse>(`${this.apiBaseUrl}/admin/llm/summary`, {
        params: this.buildUsageParams(filters)
      }).pipe(
        timeout(this.requestTimeoutMs),
        map((response) => this.mapSummary(response)),
        catchError(() => throwError(() => new Error('No fue posible cargar resumen LLM.')))
      );
    }

    return of(this.buildMockSummary(this.getMockUsageSnapshot(filters)));
  }

  getUsageSnapshot(filters?: LlmUsageFilters): Observable<{ usage: LlmUsageDailyMetric[]; summary: LlmUsageSummary }> {
    if (!environment.useMocks) {
      return forkJoin({
        usage: this.getUsage(filters),
        summary: this.getSummary(filters)
      });
    }

    const usage = this.getMockUsageSnapshot(filters);
    return of({
      usage,
      summary: this.buildMockSummary(usage)
    });
  }

  private buildUsageParams(filters?: LlmUsageFilters): HttpParams {
    let params = new HttpParams();

    if (!filters) {
      return params;
    }

    if (filters.from) {
      params = params.set('from', filters.from);
    }

    if (filters.to) {
      params = params.set('to', filters.to);
    }

    if (filters.model) {
      params = params.set('model', filters.model);
    }

    if (filters.status) {
      params = params.set('status', filters.status);
    }

    return params;
  }

  private mapConfigResponse(response: BackendLlmConfigResponse): LlmConfig {
    return {
      provider: response.provider,
      model: response.model,
      baseUrl: response.baseUrl,
      enabled: response.enabled,
      apiKeyConfigured: response.apiKeyConfigured,
      maskedApiKey: response.maskedApiKey,
      updatedAt: response.updatedAt,
      patientBehavior: this.mapPatientBehavior(response)
    };
  }

  private mapUpdatePayload(payload: UpdateLlmConfigPayload): BackendUpdateLlmConfigRequest {
    return {
      provider: payload.provider,
      model: payload.model,
      baseUrl: payload.baseUrl,
      enabled: payload.enabled,
      apiKey: payload.apiKey?.trim() ? payload.apiKey.trim() : null,
      systemPrompt: payload.patientBehavior.basePrompt?.trim() || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.basePrompt,
      patientBehaviorRules: payload.patientBehavior.additionalRules?.trim() || '',
      noInfoResponse:
        payload.patientBehavior.noInformationReply?.trim() || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.noInformationReply,
      revealStrategy: payload.patientBehavior.revealStrategy,
      maxHistoryMessages: payload.patientBehavior.maxHistoryMessages,
      temperature: payload.patientBehavior.temperature,
      maxTokens: payload.patientBehavior.maxTokens,
      enabledSafetyFilter: payload.patientBehavior.safetyFilterEnabled
    };
  }

  private mapPatientBehavior(response: BackendLlmConfigResponse): PatientBehaviorConfig {
    return {
      basePrompt: response.systemPrompt?.trim() || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.basePrompt,
      additionalRules: response.patientBehaviorRules?.trim() || '',
      noInformationReply: response.noInfoResponse?.trim() || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.noInformationReply,
      revealStrategy: response.revealStrategy || RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.revealStrategy,
      maxHistoryMessages:
        response.maxHistoryMessages >= 1
          ? response.maxHistoryMessages
          : RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.maxHistoryMessages,
      temperature:
        typeof response.temperature === 'number'
          ? response.temperature
          : RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.temperature,
      maxTokens:
        response.maxTokens >= 1
          ? response.maxTokens
          : RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.maxTokens,
      safetyFilterEnabled:
        typeof response.enabledSafetyFilter === 'boolean'
          ? response.enabledSafetyFilter
          : RECOMMENDED_PATIENT_BEHAVIOR_CONFIG.safetyFilterEnabled
    };
  }

  private mapUsageItem(item: BackendLlmUsageDailyResponse): LlmUsageDailyMetric {
    const mappedItem: LlmUsageDailyMetric & { model?: string; status?: string } = {
      date: item.date,
      tokensInput: item.tokensInput,
      tokensOutput: item.tokensOutput,
      calls: item.calls,
      avgLatencyMs: item.avgLatencyMs
    };

    if (item.model) {
      mappedItem.model = item.model;
    }

    if (item.status) {
      mappedItem.status = item.status;
    }

    return mappedItem;
  }

  private mapSummary(response: BackendLlmSummaryResponse): LlmUsageSummary {
    return {
      totalCalls: response.totalCalls,
      totalTokens: response.totalTokens,
      avgLatencyMs: response.avgLatencyMs,
      fallbackCount: response.fallbackCount,
      errorCount: response.errorCount,
      estimatedCostUsd: response.estimatedCostUsd ?? 0,
      estimatedCostClp: response.estimatedCostClp ?? 0,
      usdToClpRate: response.usdToClpRate ?? 0
    };
  }

  private buildMockSummary(usage: LlmUsageDailyMetric[]): LlmUsageSummary {
    const totalCalls = usage.reduce((total, item) => total + item.calls, 0);
    const totalTokens = usage.reduce((total, item) => total + item.tokensInput + item.tokensOutput, 0);
    const averageLatency =
      usage.reduce((total, item) => total + (item.avgLatencyMs ?? 0), 0) /
      (usage.length || 1);

    return {
      totalCalls,
      totalTokens,
      avgLatencyMs: Number.isFinite(averageLatency) ? Math.round(averageLatency) : null,
      fallbackCount: 6,
      errorCount: 2,
      estimatedCostUsd: 0,
      estimatedCostClp: 0,
      usdToClpRate: 0
    };
  }

  private getMockUsageSnapshot(filters?: LlmUsageFilters): LlmUsageDailyMetric[] {
    this.mockUsageTick += 1;
    const usage = this.mockUsage.map((item) => ({ ...item }));

    if (usage.length === 0) {
      return usage;
    }

    const latestIndex = usage.length - 1;
    const latest = usage[latestIndex];
    const callsIncrement = this.mockUsageTick;
    const inputIncrement = callsIncrement * 48;
    const outputIncrement = callsIncrement * 34;
    const latencyShift = this.mockUsageTick % 3;

    usage[latestIndex] = {
      ...latest,
      calls: latest.calls + callsIncrement,
      tokensInput: latest.tokensInput + inputIncrement,
      tokensOutput: latest.tokensOutput + outputIncrement,
      avgLatencyMs: latest.avgLatencyMs === null ? null : latest.avgLatencyMs + latencyShift
    };

    this.mockUsage = usage;
    return this.applyMockFilters(usage, filters);
  }

  private applyMockFilters(usage: LlmUsageDailyMetric[], filters?: LlmUsageFilters): LlmUsageDailyMetric[] {
    if (!filters) {
      return usage;
    }

    return usage.filter((item) => {
      const afterFrom = filters.from ? item.date >= filters.from : true;
      const beforeTo = filters.to ? item.date <= filters.to : true;
      const modelMatches = filters.model ? filters.model === this.mockConfig.model : true;
      const statusMatches = !filters.status || filters.status === 'all';

      return afterFrom && beforeTo && modelMatches && statusMatches;
    });
  }
}

interface BackendLlmConfigResponse {
  provider: string;
  model: string;
  baseUrl: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  maskedApiKey?: string | null;
  systemPrompt?: string | null;
  patientBehaviorRules?: string | null;
  noInfoResponse?: string | null;
  revealStrategy?: 'PROGRESSIVE' | 'DIRECT' | 'RESTRICTIVE' | null;
  maxHistoryMessages: number;
  temperature: number;
  maxTokens: number;
  enabledSafetyFilter: boolean;
  updatedAt: string | null;
}

interface BackendUpdateLlmConfigRequest {
  provider: string;
  model: string;
  baseUrl: string;
  enabled: boolean;
  apiKey: string | null;
  systemPrompt: string;
  patientBehaviorRules: string;
  noInfoResponse: string;
  revealStrategy: 'PROGRESSIVE' | 'DIRECT' | 'RESTRICTIVE';
  maxHistoryMessages: number;
  temperature: number;
  maxTokens: number;
  enabledSafetyFilter: boolean;
}

interface BackendTestConnectionResponse {
  success: boolean;
  message: string;
}

interface BackendLlmUsageDailyResponse {
  date: string;
  tokensInput: number;
  tokensOutput: number;
  calls: number;
  avgLatencyMs: number | null;
  model?: string;
  status?: string;
}

interface BackendLlmSummaryResponse {
  totalCalls: number;
  totalTokens: number;
  avgLatencyMs: number | null;
  fallbackCount: number;
  errorCount: number;
  estimatedCostUsd?: number;
  estimatedCostClp?: number;
  usdToClpRate?: number;
}
