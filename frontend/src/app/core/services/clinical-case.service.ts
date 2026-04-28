import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
          trigger: 'tos, respiratorio',
          visibility: 'INITIAL'
        },
        {
          category: 'Evolución',
          title: 'Fatiga de 5 días',
          trigger: 'duración, evolución',
          visibility: 'ON_QUESTION'
        },
        {
          category: 'Antecedente epidemiológico',
          title: 'Contacto con persona enferma',
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
          map((response) => this.mapBackendCaseToDetail(response)),
          catchError(() => throwError(() => new Error('No fue posible cargar el caso clínico.')))
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
      return this.http.delete<void>(`${this.apiBaseUrl}/clinical-cases/${id}`);
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
          trigger: 'tos, respiratorio',
          visibility: 'INITIAL'
        },
        {
          category: 'Evolución',
          title: 'Fatiga de 5 días',
          trigger: 'duración, evolución',
          visibility: 'ON_QUESTION'
        },
        {
          category: 'Antecedente epidemiológico',
          title: 'Contacto con persona enferma',
          trigger: 'contactos, contagio',
          visibility: 'ON_QUESTION'
        }
      ]
    });
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

    return {
      ...summary,
      age: response.patientAge ?? 0,
      sex: this.mapSex(response.patientSex),
      context: response.context ?? 'Consulta clínica',
      reason: response.chiefComplaint,
      initialMessage: response.initialMessage ?? response.chiefComplaint,
      expectedDiagnosis: response.expectedDiagnosis ?? undefined,
      fallbackResponse: response.noInformationPhrase,
      behaviorGuidelines: response.description ?? personality.behaviorNotes,
      personality,
      facts
    };
  }

  private mapUpsertPayloadToBackendRequest(payload: ClinicalCaseUpsertPayload): BackendClinicalCaseRequest {
    return {
      title: payload.title,
      description: payload.behaviorGuidelines || payload.context || null,
      patientName: payload.patientName,
      patientAge: payload.age,
      patientSex: payload.sex,
      chiefComplaint: payload.reason || payload.initialMessage,
      noInformationPhrase: payload.fallbackResponse || 'No tengo información asociada a eso.',
      active: payload.status !== 'ARCHIVED',
      facts: payload.facts.map((fact) => ({
        key: this.normalizeFactKey(fact.title),
        content: (fact.trigger || fact.title).trim(),
        revealLevel: fact.visibility === 'INITIAL' ? 1 : 2
      })),
      personality: [payload.personality.tone, payload.personality.detailLevel, payload.personality.behaviorNotes]
    };
  }

  private extractBackendFacts(response: BackendClinicalCaseResponse): BackendClinicalFact[] {
    const rawFacts = response.facts ?? response.clinicalFacts ?? [];

    return rawFacts.map((fact, index) => ({
      category: fact.category ?? fact.categoria ?? 'Hecho clínico',
      title: fact.key ?? fact.nombre ?? fact.title ?? `Hecho ${index + 1}`,
      trigger: fact.content ?? fact.contenidoPaciente ?? fact.trigger ?? this.mapTriggersToString(fact.triggers) ?? '',
      visibility: this.mapFactVisibility(fact.revealLevel ?? fact.nivelRevelacion ?? fact.visibility)
    }));
  }

  private extractBackendPersonality(response: BackendClinicalCaseResponse): ClinicalCasePersonality {
    const personality = response.personality;
    if (Array.isArray(personality)) {
      return {
        tone: personality[0] || this.getDefaultPersonality().tone,
        detailLevel: personality[1] || this.getDefaultPersonality().detailLevel,
        behaviorNotes: personality[2] || response.description || this.getDefaultPersonality().behaviorNotes
      };
    }

    if (personality) {
      return {
        tone: personality.tone ?? this.getDefaultPersonality().tone,
        detailLevel: personality.detailLevel ?? this.getDefaultPersonality().detailLevel,
        behaviorNotes: personality.behaviorNotes ?? personality.notes ?? response.description ?? this.getDefaultPersonality().behaviorNotes
      };
    }

    return {
      ...this.getDefaultPersonality(),
      behaviorNotes: response.description ?? this.getDefaultPersonality().behaviorNotes
    };
  }

  private normalizeFactKey(value: string): string {
    const normalized = value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
    return normalized || 'fact';
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

  private getDefaultPersonality(): ClinicalCasePersonality {
    return {
      tone: 'Natural y colaborador',
      detailLevel: 'Responder solo lo preguntado',
      behaviorNotes: ''
    };
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
  category: string;
  title: string;
  trigger: string;
  visibility: ClinicalFactVisibility;
}

interface BackendClinicalFactRequest {
  key: string;
  content: string;
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
