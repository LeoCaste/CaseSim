import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';

import {
  LlmConfig,
  LlmTestConnectionResult,
  LlmUsageDailyMetric,
  LlmUsageSummary,
  UpdateLlmConfigPayload
} from '../models/admin-llm.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminLlmService {
  private readonly apiBaseUrl = environment.apiBaseUrl;

  private mockConfig: LlmConfig = {
    provider: 'openai',
    model: 'gpt-4o-mini',
    baseUrl: 'https://api.openai.com/v1',
    enabled: true,
    apiKeyConfigured: true,
    maskedApiKey: '************7890',
    updatedAt: new Date().toISOString()
  };

  private readonly mockUsage: LlmUsageDailyMetric[] = [
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
          map((response) => this.mapConfigResponse(response)),
          catchError(() => of(this.mockConfig))
        );
    }

    return of(this.mockConfig);
  }

  updateConfig(payload: UpdateLlmConfigPayload): Observable<LlmConfig> {
    if (!environment.useMocks) {
      return this.http
        .put<BackendLlmConfigResponse>(`${this.apiBaseUrl}/admin/llm/config`, this.mapUpdatePayload(payload))
        .pipe(map((response) => this.mapConfigResponse(response)));
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
      updatedAt: new Date().toISOString()
    };

    return of(this.mockConfig);
  }

  testConnection(): Observable<LlmTestConnectionResult> {
    if (!environment.useMocks) {
      return this.http.post<BackendTestConnectionResponse>(`${this.apiBaseUrl}/admin/llm/test-connection`, {}).pipe(
        map((response) => ({
          success: response.success,
          message: response.message
        })),
        catchError(() =>
          of({
            success: false,
            message: 'No fue posible conectar con el proveedor LLM.'
          })
        )
      );
    }

    if (!this.mockConfig.enabled || !this.mockConfig.apiKeyConfigured) {
      return of({
        success: false,
        message: 'LLM deshabilitado o sin API key configurada.'
      });
    }

    return of({
      success: true,
      message: 'Conexión exitosa con el proveedor configurado.'
    });
  }

  getUsage(): Observable<LlmUsageDailyMetric[]> {
    if (!environment.useMocks) {
      return this.http.get<BackendLlmUsageDailyResponse[]>(`${this.apiBaseUrl}/admin/llm/usage`).pipe(
        map((response) => response.map((item) => this.mapUsageItem(item))),
        catchError(() => of(this.mockUsage))
      );
    }

    return of(this.mockUsage);
  }

  getSummary(): Observable<LlmUsageSummary> {
    if (!environment.useMocks) {
      return this.http.get<BackendLlmSummaryResponse>(`${this.apiBaseUrl}/admin/llm/summary`).pipe(
        map((response) => this.mapSummary(response)),
        catchError(() => of(this.buildMockSummary()))
      );
    }

    return of(this.buildMockSummary());
  }

  private mapConfigResponse(response: BackendLlmConfigResponse): LlmConfig {
    return {
      provider: response.provider,
      model: response.model,
      baseUrl: response.baseUrl,
      enabled: response.enabled,
      apiKeyConfigured: response.apiKeyConfigured,
      maskedApiKey: response.maskedApiKey,
      updatedAt: response.updatedAt
    };
  }

  private mapUpdatePayload(payload: UpdateLlmConfigPayload): BackendUpdateLlmConfigRequest {
    return {
      provider: payload.provider,
      model: payload.model,
      baseUrl: payload.baseUrl,
      enabled: payload.enabled,
      apiKey: payload.apiKey?.trim() ? payload.apiKey.trim() : null
    };
  }

  private mapUsageItem(item: BackendLlmUsageDailyResponse): LlmUsageDailyMetric {
    return {
      date: item.date,
      tokensInput: item.tokensInput,
      tokensOutput: item.tokensOutput,
      calls: item.calls,
      avgLatencyMs: item.avgLatencyMs
    };
  }

  private mapSummary(response: BackendLlmSummaryResponse): LlmUsageSummary {
    return {
      totalCalls: response.totalCalls,
      totalTokens: response.totalTokens,
      avgLatencyMs: response.avgLatencyMs,
      fallbackCount: response.fallbackCount,
      errorCount: response.errorCount
    };
  }

  private buildMockSummary(): LlmUsageSummary {
    const totalCalls = this.mockUsage.reduce((total, item) => total + item.calls, 0);
    const totalTokens = this.mockUsage.reduce((total, item) => total + item.tokensInput + item.tokensOutput, 0);
    const averageLatency =
      this.mockUsage.reduce((total, item) => total + (item.avgLatencyMs ?? 0), 0) /
      (this.mockUsage.length || 1);

    return {
      totalCalls,
      totalTokens,
      avgLatencyMs: Number.isFinite(averageLatency) ? Math.round(averageLatency) : null,
      fallbackCount: 6,
      errorCount: 2
    };
  }
}

interface BackendLlmConfigResponse {
  provider: string;
  model: string;
  baseUrl: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  maskedApiKey: string;
  updatedAt: string | null;
}

interface BackendUpdateLlmConfigRequest {
  provider: string;
  model: string;
  baseUrl: string;
  enabled: boolean;
  apiKey: string | null;
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
}

interface BackendLlmSummaryResponse {
  totalCalls: number;
  totalTokens: number;
  avgLatencyMs: number | null;
  fallbackCount: number;
  errorCount: number;
}
