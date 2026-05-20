import { LlmModel, LlmProvider } from './admin-llm.model';

export interface LlmProviderDefinition {
  label: string;
  defaultBaseUrl: string;
  suggestedModels: LlmModel[];
  knownModels: LlmModel[];
  defaultModel: LlmModel;
  requiresApiKey: boolean;
}

export const LLM_PROVIDER_CATALOG: Record<LlmProvider, LlmProviderDefinition> = {
  openai: {
    label: 'OpenAI',
    defaultBaseUrl: 'https://api.openai.com/v1',
    suggestedModels: ['gpt-4.1-mini', 'gpt-4.1', 'gpt-4o-mini'],
    knownModels: ['gpt-4.1-mini', 'gpt-4.1', 'gpt-4o-mini'],
    defaultModel: 'gpt-4.1-mini',
    requiresApiKey: true
  },
  'openai-compatible': {
    label: 'OpenAI Compatible',
    defaultBaseUrl: 'https://api.openai.com/v1',
    suggestedModels: ['gpt-4.1-mini', 'gpt-4.1', 'gpt-4o-mini'],
    knownModels: ['gpt-4.1-mini', 'gpt-4.1', 'gpt-4o-mini'],
    defaultModel: 'gpt-4o-mini',
    requiresApiKey: true
  },
  anthropic: {
    label: 'Anthropic',
    defaultBaseUrl: 'https://api.anthropic.com/v1',
    suggestedModels: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest'],
    knownModels: ['claude-3-5-haiku-latest', 'claude-3-5-sonnet-latest'],
    defaultModel: 'claude-3-5-haiku-latest',
    requiresApiKey: true
  },
  gemini: {
    label: 'Google Gemini',
    defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    suggestedModels: ['gemini-2.5-flash-lite', 'gemini-2.5-flash', 'gemini-2.5-pro'],
    knownModels: ['gemini-2.5-flash-lite', 'gemini-2.5-flash', 'gemini-2.5-pro'],
    defaultModel: 'gemini-2.5-flash-lite',
    requiresApiKey: true
  },
  groq: {
    label: 'Groq',
    defaultBaseUrl: 'https://api.groq.com/openai/v1',
    suggestedModels: ['llama-3.1-8b-instant', 'llama-3.3-70b-versatile'],
    knownModels: ['llama-3.1-8b-instant', 'llama-3.3-70b-versatile'],
    defaultModel: 'llama-3.1-8b-instant',
    requiresApiKey: true
  },
  openrouter: {
    label: 'OpenRouter',
    defaultBaseUrl: 'https://openrouter.ai/api/v1',
    suggestedModels: [
      'openai/gpt-4.1-mini',
      'google/gemini-2.0-flash-001',
      'anthropic/claude-3.5-sonnet',
      'meta-llama/llama-3.1-8b-instruct'
    ],
    knownModels: [
      'openai/gpt-4.1-mini',
      'google/gemini-2.0-flash-001',
      'anthropic/claude-3.5-sonnet',
      'meta-llama/llama-3.1-8b-instruct'
    ],
    defaultModel: 'openai/gpt-4.1-mini',
    requiresApiKey: true
  }
};

export const LLM_PROVIDER_LIST: LlmProvider[] = Object.keys(LLM_PROVIDER_CATALOG) as LlmProvider[];

// Proveedores habilitados explícitamente para la pantalla Admin LLM.
// Mantiene el catálogo completo para compatibilidad, pero limita UX activa.
export const ADMIN_LLM_ACTIVE_PROVIDERS: LlmProvider[] = ['openai', 'groq', 'gemini', 'openrouter'];
