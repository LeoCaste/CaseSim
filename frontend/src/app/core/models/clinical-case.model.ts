export type ClinicalFactVisibility = 'INITIAL' | 'ON_QUESTION';
export type ClinicalCaseStatus = 'DRAFT' | 'READY' | 'ARCHIVED';
export type ClinicalSex = 'F' | 'M' | 'X';

// Alias temporal para mantener compatibilidad con usos anteriores.
export type PatientSex = ClinicalSex;

export interface ClinicalFact {
  id?: string;
  /** Backend contract identifier for professor facts. */
  key?: string;
  category: string;
  title: string;
  content: string;
  /** Backend contract triggers for professor facts. */
  triggers?: string[];
  trigger: string;
  visibility: ClinicalFactVisibility;
  /** Preserva niveles backend 1..4 aunque la UI actual solo muestre Inicial/Bajo pregunta. */
  revealLevel?: number;
}

export interface ClinicalCasePersonality {
  tone: string;
  detailLevel: string;
  behaviorNotes: string;
}

export interface ClinicalCaseSummary {
  id: string;
  title: string;
  patientName: string;
  status: ClinicalCaseStatus;
  estimatedTimeMinutes?: number;
  factsCount: number;
  age?: number;
  sex?: ClinicalSex;
  reason?: string;
}

export interface ClinicalCase extends ClinicalCaseSummary {
  age: number;
  sex: ClinicalSex;
  context: string;
  reason: string;
  initialMessage: string;
  expectedDiagnosis?: string;
  /** Valor heredado desde [CASESIM_META]; solo se preserva por compatibilidad. */
  legacyExpectedDiagnosis?: string;
  fallbackResponse?: string;
  behaviorGuidelines?: string;
  personality: ClinicalCasePersonality;
  facts: ClinicalFact[];
}

export interface ClinicalCaseUpsertPayload {
  title: string;
  patientName: string;
  status: ClinicalCaseStatus;
  estimatedTimeMinutes?: number;
  age: number;
  sex: ClinicalSex;
  context: string;
  reason: string;
  initialMessage: string;
  expectedDiagnosis?: string;
  /** No crear desde UI; permite no perder metadato legacy ya existente. */
  legacyExpectedDiagnosis?: string;
  fallbackResponse?: string;
  behaviorGuidelines?: string;
  personality: ClinicalCasePersonality;
  facts: ClinicalFact[];
}
