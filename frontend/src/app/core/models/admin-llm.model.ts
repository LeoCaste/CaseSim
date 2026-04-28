export type LlmProvider = 'openai';

export type LlmModel = 'gpt-4o-mini' | 'gpt-4.1-mini' | 'gpt-4.1';

export interface LlmConfig {
  provider: LlmProvider | string;
  model: LlmModel | string;
  baseUrl: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  maskedApiKey?: string | null;
  updatedAt: string | null;
}

export interface UpdateLlmConfigPayload {
  provider: LlmProvider | string;
  model: LlmModel | string;
  baseUrl: string;
  enabled: boolean;
  apiKey?: string;
}

export interface LlmTestConnectionResult {
  success: boolean;
  message: string;
}

export interface LlmUsageDailyMetric {
  date: string;
  tokensInput: number;
  tokensOutput: number;
  calls: number;
  avgLatencyMs: number | null;
}

export interface LlmUsageSummary {
  totalCalls: number;
  totalTokens: number;
  avgLatencyMs: number | null;
  fallbackCount: number;
  errorCount: number;
  estimatedCostUsd: number;
  estimatedCostClp: number;
  usdToClpRate: number;
}

export type LlmUsageStatusFilter = 'all' | 'error' | 'fallback';

export interface LlmUsageFilters {
  from?: string;
  to?: string;
  model?: string;
  status?: LlmUsageStatusFilter;
}
