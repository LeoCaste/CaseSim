export type LlmProvider = 'openai' | 'openai-compatible' | 'anthropic' | 'gemini' | 'groq' | 'openrouter';

export type LlmModel =
  | 'gpt-4o-mini'
  | 'gpt-4.1-mini'
  | 'gpt-4.1'
  | 'claude-3-5-haiku-latest'
  | 'claude-3-5-sonnet-latest'
  | 'gemini-1.5-flash'
  | 'gemini-1.5-pro'
  | 'gemini-2.5-flash-lite'
  | 'gemini-2.5-flash'
  | 'gemini-2.5-pro'
  | 'llama-3.1-8b-instant'
  | 'llama-3.3-70b-versatile'
  | 'openai/gpt-4.1-mini'
  | 'google/gemini-2.0-flash-001'
  | 'anthropic/claude-3.5-sonnet'
  | 'meta-llama/llama-3.1-8b-instruct';

export type PatientRevealStrategy = 'PROGRESSIVE' | 'DIRECT' | 'RESTRICTIVE';

export interface PatientBehaviorConfig {
  basePrompt: string;
  additionalRules: string;
  noInformationReply: string;
  revealStrategy: PatientRevealStrategy;
  maxHistoryMessages: number;
  temperature: number;
  maxTokens: number;
  safetyFilterEnabled: boolean;
}

export const RECOMMENDED_PATIENT_BEHAVIOR_CONFIG: PatientBehaviorConfig = {
  basePrompt:
    'Responde como paciente estandarizado en contexto clínico. Entrega información de forma coherente con el motivo de consulta y evolución del caso.',
  additionalRules:
    'No inventes antecedentes críticos no definidos en el caso. Mantén consistencia con edad, sexo y contexto clínico.',
  noInformationReply: 'No tengo información sobre eso en este momento.',
  revealStrategy: 'PROGRESSIVE',
  maxHistoryMessages: 6,
  temperature: 0.4,
  maxTokens: 350,
  safetyFilterEnabled: true
};

export interface LlmConfig {
  provider: LlmProvider | string;
  model: LlmModel | string;
  baseUrl: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  maskedApiKey?: string | null;
  updatedAt: string | null;
  patientBehavior: PatientBehaviorConfig;
}

export interface UpdateLlmConfigPayload {
  provider: LlmProvider | string;
  model: LlmModel | string;
  baseUrl: string;
  enabled: boolean;
  apiKey?: string;
  patientBehavior: PatientBehaviorConfig;
}

export interface LlmTestConnectionResult {
  success: boolean;
  message: string;
  statusCode?: number;
  errorCode?: string;
  traceId?: string;
  retryable?: boolean;
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
