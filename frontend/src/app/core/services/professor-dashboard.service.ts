import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { ProfessorDashboard, ProfessorSessionReview } from '../models/professor-dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class ProfessorDashboardService {
  getDashboard(): Observable<ProfessorDashboard> {
    return of({
      summary: [
        { label: 'Simulación activa', value: '1' },
        { label: 'Estudiantes asignados', value: '32' },
        { label: 'Sesiones en curso', value: '8' },
        { label: 'Sesiones completadas', value: '18' }
      ],
      simulations: [
        {
          name: 'Entrevista respiratoria',
          caseName: 'Catalina Paz Soto',
          course: 'Caso de prueba',
          students: 32,
          completedSessions: 18
        }
      ],
      activities: [
        {
          title: 'Entrevista respiratoria',
          course: 'Caso de prueba',
          caseName: 'Catalina Paz Soto',
          status: 'Activa',
          completed: 18,
          total: 32
        },
        {
          title: 'Caso cardiovascular',
          course: 'Caso de prueba',
          caseName: 'Roberto Alarcón',
          status: 'Borrador',
          completed: 0,
          total: 28
        }
      ],
      sessions: [
        {
          id: 'sess-01',
          student: 'Diego Muñoz',
          activity: 'Entrevista respiratoria',
          status: 'Completada',
          turns: 18,
          duration: '12 min'
        },
        {
          id: 'sess-02',
          student: 'Valentina Ríos',
          activity: 'Entrevista respiratoria',
          status: 'En curso',
          turns: 9,
          duration: '7 min'
        }
      ]
    });
  }

  getProfessorSessionReview(_sessionId: string): Observable<ProfessorSessionReview> {
    return of({
      session: {
        id: 'sess-01',
        studentName: 'Diego Muñoz',
        activityName: 'Entrevista respiratoria',
        caseName: 'Catalina Paz Soto',
        status: 'COMPLETED',
        durationMinutes: 12,
        turns: 18,
        submittedAt: '25/04/2026 · 10:42'
      },
      transcript: [
        {
          id: 'm-01',
          role: 'PATIENT',
          timestamp: '10:30',
          content: 'Vengo por tos seca y agotamiento desde hace cinco días.'
        },
        {
          id: 'm-02',
          role: 'STUDENT',
          timestamp: '10:31',
          content: 'Gracias por comentarlo. ¿Desde cuándo nota que la tos empeora?'
        },
        {
          id: 'm-03',
          role: 'PATIENT',
          timestamp: '10:32',
          content: 'Empeora en la noche y me cuesta dormir bien.'
        },
        {
          id: 'm-04',
          role: 'STUDENT',
          timestamp: '10:34',
          content: '¿Ha tenido fiebre, dolor torácico o dificultad para respirar?'
        },
        {
          id: 'm-05',
          role: 'PATIENT',
          timestamp: '10:35',
          content:
            'He tenido algo de fiebre baja y me canso más de lo habitual, pero no he sentido dolor fuerte en el pecho.'
        }
      ],
      notebook: {
        notes:
          'Paciente consulta por tos seca persistente y fatiga. Refiere empeoramiento nocturno y alteración del sueño. Pendiente explorar antecedentes respiratorios y contactos.',
        hypothesis: 'Cuadro respiratorio subagudo. Considerar infección respiratoria atípica como hipótesis inicial.'
      },
      diagnosis: {
        finalDiagnosis: 'Neumonía atípica probable',
        reasoning:
          'Tos seca de varios días, fatiga, fiebre baja y evolución subaguda orientan a cuadro respiratorio compatible con neumonía atípica.'
      }
    });
  }

  saveEvaluation(_sessionId: string, _feedback: string): Observable<boolean> {
    return of(true);
  }
}
