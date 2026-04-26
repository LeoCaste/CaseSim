export type ClinicalFactVisibility = 'INITIAL' | 'ON_QUESTION';
export type ClinicalCaseStatus = 'DRAFT' | 'READY' | 'ARCHIVED';
export type PatientSex = 'F' | 'M' | 'X';

export interface ClinicalFact {
  id?: string;
  category: string;
  title: string;
  trigger: string;
  visibility: ClinicalFactVisibility;
}

export interface ClinicalCaseSummary {
  id: string;
  title: string;
  patientName: string;
  status: ClinicalCaseStatus;
  estimatedTimeMinutes?: number;
  factsCount: number;
}

export interface ClinicalCase extends ClinicalCaseSummary {
  age: number;
  sex: PatientSex;
  context: string;
  reason: string;
  initialMessage: string;
  expectedDiagnosis?: string;
  fallbackResponse?: string;
  behaviorGuidelines?: string;
  facts: ClinicalFact[];
}
