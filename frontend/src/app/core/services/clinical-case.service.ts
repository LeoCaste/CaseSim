import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { ClinicalCase, ClinicalCaseSummary } from '../models/clinical-case.model';

export interface ClinicalCaseCardView {
  id: string;
  patientName: string;
  title: string;
  age: number;
  sex: string;
  reason: string;
  status: 'Listo' | 'Borrador' | 'Archivado';
  difficulty: string;
  estimatedTime: string;
  facts: number;
}

@Injectable({
  providedIn: 'root'
})
export class ClinicalCaseService {
  private readonly clinicalCases: ClinicalCase[] = [
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

  getClinicalCases(): Observable<ClinicalCaseSummary[]> {
    return of(
      this.clinicalCases.map((clinicalCase) => ({
        id: clinicalCase.id,
        title: clinicalCase.title,
        patientName: clinicalCase.patientName,
        status: clinicalCase.status,
        estimatedTimeMinutes: clinicalCase.estimatedTimeMinutes,
        factsCount: clinicalCase.factsCount
      }))
    );
  }

  getClinicalCaseCards(): Observable<ClinicalCaseCardView[]> {
    return of(
      this.clinicalCases.map<ClinicalCaseCardView>((clinicalCase) => ({
        id: clinicalCase.id,
        patientName: clinicalCase.patientName,
        title: clinicalCase.title,
        age: clinicalCase.age,
        sex: clinicalCase.sex,
        reason: clinicalCase.reason,
        status: this.mapCaseStatusToCardStatus(clinicalCase.status),
        difficulty: 'Formativo',
        estimatedTime:
          clinicalCase.estimatedTimeMinutes === undefined
            ? 'Sin límite de tiempo'
            : `${clinicalCase.estimatedTimeMinutes} minutos`,
        facts: clinicalCase.factsCount
      }))
    );
  }

  private mapCaseStatusToCardStatus(status: ClinicalCase['status']): ClinicalCaseCardView['status'] {
    if (status === 'READY') {
      return 'Listo';
    }

    if (status === 'DRAFT') {
      return 'Borrador';
    }

    return 'Archivado';
  }

  getClinicalCaseById(caseId: string): Observable<ClinicalCase | null> {
    const clinicalCase = this.clinicalCases.find((item) => item.id === caseId) ?? null;
    return of(clinicalCase);
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

  upsertClinicalCase(payload: ClinicalCase): Observable<ClinicalCase> {
    return of(payload);
  }
}
