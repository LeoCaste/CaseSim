import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of } from 'rxjs';

import {
  ClinicalCase,
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

  getClinicalCases(): Observable<ClinicalCaseSummary[]> {
    if (!environment.useMocks) {
      return this.http
        .get<BackendClinicalCaseResponse[]>(`${this.apiBaseUrl}/clinical-cases`)
        .pipe(
          map((response) => response.map((item) => this.mapBackendCaseToSummary(item))),
          catchError(() => of([]))
        );
    }

    return of(
      this.clinicalCases.map((clinicalCase) => ({
        id: clinicalCase.id,
        title: clinicalCase.title,
        patientName: clinicalCase.patientName,
        status: clinicalCase.status,
        estimatedTimeMinutes: clinicalCase.estimatedTimeMinutes,
        factsCount: clinicalCase.factsCount,
        age: clinicalCase.age,
        sex: clinicalCase.sex,
        reason: clinicalCase.reason
      }))
    );
  }

  getClinicalCaseById(id: string): Observable<ClinicalCase | undefined> {
    if (!environment.useMocks) {
      return this.http
        .get<BackendClinicalCaseResponse>(`${this.apiBaseUrl}/clinical-cases/${id}`)
        .pipe(
          map((response) => this.mapBackendCaseToDetail(response)),
          catchError(() => of(undefined))
        );
    }

    return of(this.clinicalCases.find((item) => item.id === id));
  }

  createClinicalCase(payload: ClinicalCaseUpsertPayload): Observable<ClinicalCase> {
    // En FASE 6 solo se integra lectura de casos contra backend.
    // Se mantiene comportamiento mock para no romper el flujo de profesor.
    const createdCase: ClinicalCase = {
      id: this.nextId(),
      ...payload,
      factsCount: payload.facts.length
    };

    this.clinicalCases = [createdCase, ...this.clinicalCases];
    return of(createdCase);
  }

  updateClinicalCase(id: string, payload: ClinicalCaseUpsertPayload): Observable<ClinicalCase> {
    // En FASE 6 solo se integra lectura de casos contra backend.
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

  deleteClinicalCase(id: string): Observable<void> {
    // En FASE 6 solo se integra lectura de casos contra backend.
    this.clinicalCases = this.clinicalCases.filter((clinicalCase) => clinicalCase.id !== id);
    return of(void 0);
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

  private mapBackendCaseToSummary(response: BackendClinicalCaseResponse): ClinicalCaseSummary {
    return {
      id: response.id,
      title: response.title,
      patientName: response.patientName,
      status: 'READY',
      estimatedTimeMinutes: undefined,
      factsCount: 0,
      age: response.patientAge,
      sex: this.mapSex(response.patientSex),
      reason: response.chiefComplaint
    };
  }

  private mapBackendCaseToDetail(response: BackendClinicalCaseResponse): ClinicalCase {
    const summary = this.mapBackendCaseToSummary(response);
    return {
      ...summary,
      age: response.patientAge ?? 0,
      sex: this.mapSex(response.patientSex),
      context: 'Consulta clínica',
      reason: response.chiefComplaint,
      initialMessage: response.chiefComplaint,
      expectedDiagnosis: undefined,
      fallbackResponse: response.noInformationPhrase,
      behaviorGuidelines: response.description,
      facts: []
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
  description: string;
  patientName: string;
  patientAge: number;
  patientSex: string;
  chiefComplaint: string;
  noInformationPhrase: string;
  active: boolean;
  createdAt: string;
}
