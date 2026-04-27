export interface LlmConfig {
  provider: string;
  model: string;
  baseUrl: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  maskedApiKey: string;
  updatedAt: string | null;
}

export interface UpdateLlmConfigPayload {
  provider: string;
  model: string;
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
}
