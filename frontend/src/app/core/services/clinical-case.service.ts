import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, throwError, timeout } from 'rxjs';

import {
  ClinicalCase,
  ClinicalCasePersonality,
  ClinicalCaseStatus,
  ClinicalCaseSummary,
  ClinicalCaseUpsertPayload,
  ClinicalFactVisibility
} from '../models/clinical-case.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ClinicalCaseService {
  private readonly requestTimeoutMs = 8000;
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly descriptionMetadataPrefix = '[CASESIM_META]';

  private clinicalCases: ClinicalCase[] = [
    {
      id: '1',
      title: 'Entrevista respiratoria',
      patientName: 'Catalina Paz Soto',
      status: 'READY',
      estimatedTimeMinutes: undefined,
      factsCount: 8,
      age: 22,
      sex: 'F',
      context: 'Consulta ambulatoria',
      reason: 'tos seca y fatiga',
      initialMessage: 'Vengo porque tengo una tos seca que no se me pasa y me siento muy agotada.',
      expectedDiagnosis: 'Neumonía atípica probable',
      fallbackResponse: 'No estoy segura, no sabría decir.',
      behaviorGuidelines:
        'Paciente responde de forma natural, breve y coherente. No entrega información que no se le haya preguntado. Puede mostrar leve preocupación por su estado.',
      personality: {
        tone: 'Natural y colaborador',
        detailLevel: 'Responder solo lo preguntado',
        behaviorNotes:
          'Paciente responde de forma natural, breve y coherente. No entrega información que no se le haya preguntado. Puede mostrar leve preocupación por su estado.'
      },
      facts: [
        {
          category: 'Síntoma',
          title: 'Tos seca persistente',
          content: 'Refiere tos seca persistente de inicio insidioso, sin expectoración.',
          trigger: 'tos, respiratorio',
          visibility: 'INITIAL'
        },
        {
          category: 'Evolución',
          title: 'Fatiga de 5 días',
          content: 'Se siente fatigada desde hace 5 días, con mayor cansancio al final del día.',
          trigger: 'duración, evolución',
          visibility: 'ON_QUESTION'
        },
        {
          category: 'Antecedente epidemiológico',
          title: 'Contacto con persona enferma',
          content: 'Tuvo contacto estrecho con un familiar con cuadro respiratorio la semana pasada.',
          trigger: 'contactos, contagio',
          visibility: 'ON_QUESTION'
        }
      ]
    },
    {
      id: '2',
      title: 'Caso cardiovascular',
      patientName: 'Roberto Alarcón',
      status: 'DRAFT',
      estimatedTimeMinutes: 20,
      factsCount: 5,
      age: 58,
      sex: 'M',
      context: 'Urgencia básica',
      reason: 'Dolor torácico intermitente',
      initialMessage: 'Desde ayer siento dolor en el pecho por momentos.',
      expectedDiagnosis: 'Síndrome coronario a descartar',
      fallbackResponse: 'No recuerdo más detalles.',
      behaviorGuidelines: 'Paciente responde con ansiedad moderada y describe síntomas en episodios.',
      personality: {
        tone: 'Ansioso',
        detailLevel: 'Dar contexto moderado',
        behaviorNotes: 'Paciente responde con ansiedad moderada y describe síntomas en episodios.'
      },
      facts: [
        {
          category: 'Síntoma',
          title: 'Dolor torácico intermitente',
          content: 'Describe episodios de dolor opresivo retroesternal de minutos de duración.',
          trigger: 'dolor, pecho',
          visibility: 'INITIAL'
        }
      ]
    }
  ];

  constructor(private http: HttpClient) {}

  getAll(): Observable<ClinicalCaseSummary[]> {
    if (!environment.useMocks) {
      return this.http
        .get<BackendClinicalCaseResponse[]>(`${this.apiBaseUrl}/clinical-cases`)
        .pipe(
          timeout(this.requestTimeoutMs),
          map((response) => response.map((item) => this.mapBackendCaseToSummary(item))),
          catchError(() => throwError(() => new Error('No fue posible cargar los casos clínicos.')))
        );
    }

    return of(this.mapMockCasesToSummary());
  }

  getById(id: string): Observable<ClinicalCase | undefined> {
    if (!environment.useMocks) {
      return this.http
        .get<BackendClinicalCaseResponse>(`${this.apiBaseUrl}/clinical-cases/${id}`)
        .pipe(
          timeout(this.requestTimeoutMs),
          map((response) => this.mapBackendCaseToDetail(response))
        );
    }

    return of(this.clinicalCases.find((item) => item.id === id));
  }

  create(payload: ClinicalCaseUpsertPayload): Observable<ClinicalCase> {
    if (!environment.useMocks) {
      return this.http
        .post<BackendClinicalCaseResponse>(
          `${this.apiBaseUrl}/clinical-cases`,
          this.mapUpsertPayloadToBackendRequest(payload)
        )
        .pipe(map((response) => this.mapBackendCaseToDetail(response)));
    }

    const createdCase: ClinicalCase = {
      id: this.nextId(),
      ...payload,
      factsCount: payload.facts.length
    };

    this.clinicalCases = [createdCase, ...this.clinicalCases];
    return of(createdCase);
  }

  update(id: string, payload: ClinicalCaseUpsertPayload): Observable<ClinicalCase> {
    if (!environment.useMocks) {
      return this.http
        .put<BackendClinicalCaseResponse>(
          `${this.apiBaseUrl}/clinical-cases/${id}`,
          this.mapUpsertPayloadToBackendRequest(payload)
        )
        .pipe(map((response) => this.mapBackendCaseToDetail(response)));
    }

    const updatedCase: ClinicalCase = {
      id,
      ...payload,
      factsCount: payload.facts.length
    };

    this.clinicalCases = this.clinicalCases.map((clinicalCase) =>
      clinicalCase.id === id ? updatedCase : clinicalCase
    );

    return of(updatedCase);
  }

  delete(id: string): Observable<void> {
    if (!environment.useMocks) {
      return this.http
        .delete<void>(`${this.apiBaseUrl}/clinical-cases/${id}`)
        .pipe(
          timeout(this.requestTimeoutMs),
          catchError((error: unknown) => {
            if (error instanceof HttpErrorResponse) {
              return throwError(() => error);
            }

            return throwError(() => new Error('No fue posible eliminar el caso clínico.'));
          })
        );
    }

    this.clinicalCases = this.clinicalCases.filter((clinicalCase) => clinicalCase.id !== id);
    return of(void 0);
  }

  getClinicalCases(): Observable<ClinicalCaseSummary[]> {
    return this.getAll();
  }

  getClinicalCaseById(id: string): Observable<ClinicalCase | undefined> {
    return this.getById(id);
  }

  createClinicalCase(payload: ClinicalCaseUpsertPayload): Observable<ClinicalCase> {
    return this.create(payload);
  }

  updateClinicalCase(id: string, payload: ClinicalCaseUpsertPayload): Observable<ClinicalCase> {
    return this.update(id, payload);
  }

  deleteClinicalCase(id: string): Observable<void> {
    return this.delete(id);
  }

  getCaseDraft(): Observable<ClinicalCase> {
    if (!environment.useMocks) {
      return of(this.buildEmptyDraft());
    }

    return of({
      id: 'draft-01',
      title: 'Entrevista respiratoria',
      patientName: 'Catalina Paz Soto',
      status: 'DRAFT',
      estimatedTimeMinutes: undefined,
      factsCount: 3,
      age: 22,
      sex: 'F',
      context: 'Consulta ambulatoria',
      reason: 'Tos seca persistente y fatiga de 5 días.',
      initialMessage: 'Vengo porque tengo una tos seca que no se me pasa y me siento muy agotada.',
      expectedDiagnosis: 'Neumonía atípica probable',
      fallbackResponse: 'No estoy segura, no sabría decir.',
      behaviorGuidelines:
        'Paciente responde de forma natural, breve y coherente. No entrega información no solicitada.',
      personality: {
        tone: 'Natural y colaborador',
        detailLevel: 'Responder solo lo preguntado',
        behaviorNotes:
          'Paciente responde de forma natural, breve y coherente. No entrega información no solicitada.'
      },
      facts: [
        {
          category: 'Síntoma',
          title: 'Tos seca persistente',
          content: 'Refiere tos seca persistente de inicio insidioso, sin expectoración.',
          trigger: 'tos, respiratorio',
          visibility: 'INITIAL'
        },
        {
          category: 'Evolución',
          title: 'Fatiga de 5 días',
          content: 'Se siente fatigada desde hace 5 días, con mayor cansancio al final del día.',
          trigger: 'duración, evolución',
          visibility: 'ON_QUESTION'
        },
        {
          category: 'Antecedente epidemiológico',
          title: 'Contacto con persona enferma',
          content: 'Tuvo contacto estrecho con un familiar con cuadro respiratorio la semana pasada.',
          trigger: 'contactos, contagio',
          visibility: 'ON_QUESTION'
        }
      ]
    });
  }

  private buildEmptyDraft(): ClinicalCase {
    return {
      id: 'draft-local',
      title: '',
      patientName: '',
      status: 'DRAFT',
      estimatedTimeMinutes: undefined,
      factsCount: 0,
      age: 18,
      sex: 'F',
      context: '',
      reason: '',
      initialMessage: '',
      expectedDiagnosis: '',
      fallbackResponse: '',
      behaviorGuidelines: '',
      personality: {
        tone: 'Natural y colaborador',
        detailLevel: 'Responder solo lo preguntado',
        behaviorNotes: ''
      },
      facts: []
    };
  }

  getStatusLabel(status: ClinicalCaseStatus): 'Listo' | 'Borrador' | 'Archivado' {
    if (status === 'READY') return 'Listo';
    if (status === 'DRAFT') return 'Borrador';
    return 'Archivado';
  }

  getFactVisibilityLabel(visibility: ClinicalFactVisibility): 'Inicial' | 'Bajo pregunta' {
    return visibility === 'INITIAL' ? 'Inicial' : 'Bajo pregunta';
  }

  private nextId(): string {
    const maxNumericId = this.clinicalCases.reduce((max, item) => {
      const numericId = Number(item.id);
      if (Number.isNaN(numericId)) {
        return max;
      }
      return Math.max(max, numericId);
    }, 0);

    return String(maxNumericId + 1);
  }

  private mapMockCasesToSummary(): ClinicalCaseSummary[] {
    return this.clinicalCases.map((clinicalCase) => ({
      id: clinicalCase.id,
      title: clinicalCase.title,
      patientName: clinicalCase.patientName,
      status: clinicalCase.status,
      estimatedTimeMinutes: clinicalCase.estimatedTimeMinutes,
      factsCount: clinicalCase.factsCount,
      age: clinicalCase.age,
      sex: clinicalCase.sex,
      reason: clinicalCase.reason
    }));
  }

  private mapBackendCaseToSummary(response: BackendClinicalCaseResponse): ClinicalCaseSummary {
    const facts = this.extractBackendFacts(response);

    return {
      id: response.id,
      title: response.title,
      patientName: response.patientName,
      status: 'READY',
      estimatedTimeMinutes: undefined,
      factsCount: response.factsCount ?? facts.length,
      age: response.patientAge,
      sex: this.mapSex(response.patientSex),
      reason: response.chiefComplaint
    };
  }

  private mapBackendCaseToDetail(response: BackendClinicalCaseResponse): ClinicalCase {
    const summary = this.mapBackendCaseToSummary(response);
    const personality = this.extractBackendPersonality(response);
    const facts = this.extractBackendFacts(response);
    const descriptionMetadata = this.parseDescriptionMetadata(response.description);
    const normalizedDescription = this.normalizeOptionalText(response.description);
    const normalizedFallbackPhrase = this.normalizeOptionalText(response.noInformationPhrase);
    const behaviorGuidelines = descriptionMetadata?.behaviorGuidelines ?? normalizedDescription;

    return {
      ...summary,
      age: response.patientAge ?? 0,
      sex: this.mapSex(response.patientSex),
      context: descriptionMetadata?.context ?? this.normalizeOptionalText(response.context) ?? '',
      reason: this.normalizeOptionalText(response.chiefComplaint) ?? '',
      initialMessage:
        descriptionMetadata?.initialMessage ?? this.normalizeOptionalText(response.initialMessage) ?? '',
      expectedDiagnosis: descriptionMetadata?.expectedDiagnosis ?? response.expectedDiagnosis ?? undefined,
      legacyExpectedDiagnosis: descriptionMetadata?.expectedDiagnosis,
      fallbackResponse: normalizedFallbackPhrase ?? undefined,
      behaviorGuidelines: behaviorGuidelines ?? undefined,
      personality,
      facts
    };
  }

  private mapUpsertPayloadToBackendRequest(payload: ClinicalCaseUpsertPayload): BackendClinicalCaseRequest {
    const normalizedFallbackResponse = this.normalizeOptionalText(payload.fallbackResponse);
    const normalizedReason = this.normalizeOptionalText(payload.reason);
    const normalizedInitialMessage = this.normalizeOptionalText(payload.initialMessage);
    const serializedDescription = this.serializeDescriptionMetadata(payload);

    return {
      title: payload.title,
      description: serializedDescription,
      patientName: payload.patientName,
      patientAge: payload.age,
      patientSex: payload.sex,
      chiefComplaint: normalizedReason ?? normalizedInitialMessage ?? '',
      noInformationPhrase: normalizedFallbackResponse ?? 'No tengo información asociada a eso.',
      active: payload.status !== 'ARCHIVED',
      facts: payload.facts
        .filter((fact) => this.normalizeOptionalText(fact.content))
        .map((fact) => ({
          category: this.normalizeOptionalText(fact.category) ?? 'GENERAL',
          key: (this.normalizeOptionalText(fact.key) ?? this.normalizeOptionalText(fact.title) ?? this.normalizeFactKey(fact.title)).trim(),
          content: (this.normalizeOptionalText(fact.content) ?? '').trim(),
          triggers: this.normalizeTriggers(fact.triggers, fact.trigger),
          revealLevel: this.resolveFactRevealLevel(fact.revealLevel, fact.visibility)
        })),
      personality: [payload.personality.tone, payload.personality.detailLevel, payload.personality.behaviorNotes]
    };
  }

  private serializeDescriptionMetadata(payload: ClinicalCaseUpsertPayload): string | null {
    const metadata: ClinicalCaseDescriptionMetadata = {
      context: this.normalizeOptionalText(payload.context),
      initialMessage: this.normalizeOptionalText(payload.initialMessage),
      expectedDiagnosis: this.normalizeOptionalText(payload.legacyExpectedDiagnosis),
      behaviorGuidelines: this.normalizeOptionalText(payload.behaviorGuidelines)
    };

    const hasAtLeastOneValue = Object.values(metadata).some((value) => typeof value === 'string');
    if (!hasAtLeastOneValue) {
      return null;
    }

    return `${this.descriptionMetadataPrefix}${JSON.stringify(metadata)}`;
  }

  private parseDescriptionMetadata(
    description: string | null | undefined
  ): ClinicalCaseDescriptionMetadata | undefined {
    const normalizedDescription = this.normalizeOptionalText(description);
    if (!normalizedDescription?.startsWith(this.descriptionMetadataPrefix)) {
      return undefined;
    }

    const payload = normalizedDescription.slice(this.descriptionMetadataPrefix.length);
    try {
      const parsed = JSON.parse(payload) as ClinicalCaseDescriptionMetadata;
      return {
        context: this.normalizeOptionalText(parsed.context),
        initialMessage: this.normalizeOptionalText(parsed.initialMessage),
        expectedDiagnosis: this.normalizeOptionalText(parsed.expectedDiagnosis),
        behaviorGuidelines: this.normalizeOptionalText(parsed.behaviorGuidelines)
      };
    } catch {
      return undefined;
    }
  }

  private normalizeOptionalText(value: string | null | undefined): string | undefined {
    if (typeof value !== 'string') {
      return undefined;
    }

    const normalized = value.trim();
    return normalized.length > 0 ? normalized : undefined;
  }

  private extractBackendFacts(response: BackendClinicalCaseResponse): BackendClinicalFact[] {
    const rawFacts = response.facts ?? response.clinicalFacts ?? [];

    return rawFacts.map((fact, index) => ({
      key: fact.key ?? fact.nombre ?? fact.title ?? `Hecho ${index + 1}`,
      category: fact.category ?? fact.categoria ?? 'Hecho clínico',
      title: fact.key ?? fact.nombre ?? fact.title ?? `Hecho ${index + 1}`,
      content: fact.content ?? fact.contenidoPaciente ?? fact.trigger ?? this.mapTriggersToString(fact.triggers) ?? '',
      triggers: fact.triggers ?? this.parseTriggerText(fact.trigger ?? fact.disparador),
      trigger: fact.trigger ?? fact.disparador ?? this.mapTriggersToString(fact.triggers) ?? fact.content ?? '',
      visibility: this.mapFactVisibility(fact.revealLevel ?? fact.nivelRevelacion ?? fact.visibility),
      revealLevel: this.normalizeRevealLevel(fact.revealLevel ?? fact.nivelRevelacion ?? fact.visibility)
    }));
  }

  private extractBackendPersonality(response: BackendClinicalCaseResponse): ClinicalCasePersonality {
    const personality = response.personality;
    const plainLegacyDescription = this.getPlainLegacyDescription(response.description);
    if (Array.isArray(personality)) {
      return {
        tone: this.normalizeOptionalText(personality[0]) ?? '',
        detailLevel: this.normalizeOptionalText(personality[1]) ?? '',
        behaviorNotes: this.normalizeOptionalText(personality[2]) ?? plainLegacyDescription ?? ''
      };
    }

    if (personality) {
      return {
        tone: this.normalizeOptionalText(personality.tone) ?? '',
        detailLevel: this.normalizeOptionalText(personality.detailLevel) ?? '',
        behaviorNotes:
          this.normalizeOptionalText(personality.behaviorNotes) ??
          this.normalizeOptionalText(personality.notes) ??
          plainLegacyDescription ??
          ''
      };
    }

    return {
      tone: '',
      detailLevel: '',
      behaviorNotes: plainLegacyDescription ?? ''
    };
  }

  private getPlainLegacyDescription(description: string | null | undefined): string | undefined {
    if (this.parseDescriptionMetadata(description)) {
      return undefined;
    }

    return this.normalizeOptionalText(description);
  }

  private normalizeFactKey(value: string): string {
    const normalized = value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
    return normalized || 'fact';
  }

  private parseTriggerText(trigger: string | null | undefined): string[] {
    const normalized = this.normalizeOptionalText(trigger);
    if (!normalized) {
      return [];
    }

    return normalized
      .split(',')
      .map((item) => this.normalizeOptionalText(item))
      .filter((item): item is string => Boolean(item));
  }

  private normalizeTriggers(triggers: string[] | null | undefined, triggerText: string | null | undefined): string[] {
    if (Array.isArray(triggers)) {
      return triggers
        .map((item) => this.normalizeOptionalText(item))
        .filter((item): item is string => Boolean(item));
    }

    return this.parseTriggerText(triggerText);
  }

  private resolveFactRevealLevel(revealLevel: number | null | undefined, visibility: ClinicalFactVisibility): number {
    if (typeof revealLevel === 'number' && Number.isFinite(revealLevel) && revealLevel >= 1 && revealLevel <= 4) {
      return Math.trunc(revealLevel);
    }

    return visibility === 'INITIAL' ? 1 : 2;
  }

  private normalizeRevealLevel(value: string | number | null | undefined): number {
    if (typeof value === 'number' && Number.isFinite(value) && value >= 1 && value <= 4) {
      return Math.trunc(value);
    }

    const normalized = (value ?? '').toString().trim().toUpperCase();
    if (normalized === 'INITIAL' || normalized === 'INICIAL' || normalized === '1') {
      return 1;
    }

    const parsed = Number(normalized);
    if (Number.isFinite(parsed) && parsed >= 1 && parsed <= 4) {
      return Math.trunc(parsed);
    }

    return 2;
  }

  private mapTriggersToString(triggers: string[] | null | undefined): string | undefined {
    if (!triggers || triggers.length === 0) {
      return undefined;
    }
    return triggers.join(', ');
  }

  private mapFactVisibility(visibility: string | number | null | undefined): ClinicalFactVisibility {
    if (typeof visibility === 'number') {
      return visibility <= 1 ? 'INITIAL' : 'ON_QUESTION';
    }

    const normalized = (visibility ?? '').toString().trim().toUpperCase();
    if (normalized === 'INITIAL' || normalized === 'INICIAL' || normalized === '1') {
      return 'INITIAL';
    }

    return 'ON_QUESTION';
  }

  private mapSex(patientSex: string | null | undefined): 'F' | 'M' | 'X' {
    if (!patientSex) return 'X';

    const normalized = patientSex.trim().toUpperCase();
    if (normalized.startsWith('F')) return 'F';
    if (normalized.startsWith('M')) return 'M';
    return 'X';
  }
}

interface BackendClinicalCaseResponse {
  id: string;
  title: string;
  description?: string;
  patientName: string;
  patientAge: number;
  patientSex: string;
  chiefComplaint: string;
  noInformationPhrase: string;
  active: boolean;
  createdAt: string;
  context?: string;
  initialMessage?: string;
  expectedDiagnosis?: string;
  factsCount?: number;
  facts?: BackendClinicalFactResponse[];
  clinicalFacts?: BackendClinicalFactResponse[];
  personality?: string[] | BackendClinicalCasePersonalityResponse;
}

interface BackendClinicalCaseRequest {
  title: string;
  description: string | null;
  patientName: string;
  patientAge: number;
  patientSex: string;
  chiefComplaint: string;
  noInformationPhrase: string;
  active: boolean;
  facts: BackendClinicalFactRequest[];
  personality: string[];
}

interface BackendClinicalFact {
  key?: string;
  category: string;
  title: string;
  content: string;
  triggers?: string[];
  trigger: string;
  visibility: ClinicalFactVisibility;
  revealLevel?: number;
}

interface BackendClinicalFactRequest {
  category: string;
  key: string;
  content: string;
  triggers: string[];
  revealLevel: number;
}

interface BackendClinicalFactResponse {
  key?: string;
  category?: string;
  categoria?: string;
  title?: string;
  nombre?: string;
  content?: string;
  contenidoPaciente?: string;
  trigger?: string;
  disparador?: string;
  triggers?: string[];
  visibility?: string;
  revealLevel?: number;
  nivelRevelacion?: number;
}

interface BackendClinicalCasePersonalityResponse {
  tone?: string;
  detailLevel?: string;
  behaviorNotes?: string;
  notes?: string;
}

interface ClinicalCaseDescriptionMetadata {
  context?: string;
  initialMessage?: string;
  expectedDiagnosis?: string;
  behaviorGuidelines?: string;
}
