import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { StudentActivity, StudentSession } from '../models/student-session.model';

export interface StudentDashboardData {
  activities: StudentActivity[];
  history: Array<{
    title: string;
    patient: string;
    status: string;
    date: string;
    route: string;
  }>;
}

@Injectable({
  providedIn: 'root'
})
export class StudentSessionService {
  getDashboardData(): Observable<StudentDashboardData> {
    return of({
      activities: [
        {
          id: 'act-01',
          title: 'Caso de prueba respiratorio',
          course: 'Caso de prueba',
          professor: 'Equipo docente',
          patient: 'Catalina Paz Soto',
          status: 'Disponible',
          statusType: 'success',
          duration: 'Sin límite de tiempo',
          description:
            'Realiza una anamnesis clínica y concluye la atención cuando tengas una hipótesis diagnóstica.',
          actionLabel: 'Iniciar entrevista',
          route: '/student/waiting-room'
        },
        {
          id: 'act-02',
          title: 'Caso de prueba cardiovascular',
          course: 'Caso de prueba',
          professor: 'Equipo docente',
          patient: 'Roberto Alarcón',
          status: 'Pendiente',
          statusType: 'neutral',
          duration: '20 minutos',
          description: 'Actividad de prueba pendiente de habilitación.',
          actionLabel: 'No disponible',
          route: null
        }
      ],
      history: [
        {
          title: 'Caso de prueba respiratorio',
          patient: 'Catalina Paz Soto',
          status: 'Registrada',
          date: '25/04/2026',
          route: '/student/session-detail'
        }
      ]
    });
  }

  getStudentSessionDetail(_sessionId: string): Observable<StudentSession> {
    return of({
      id: 'sess-01',
      title: 'Caso Catalina Paz Soto',
      patientName: 'Catalina Paz Soto',
      status: 'COMPLETED',
      submittedAt: '2026-04-25T10:42:00Z',
      notes: 'Paciente con tos seca persistente, fatiga y empeoramiento nocturno.',
      hypothesis: 'Cuadro respiratorio subagudo.',
      diagnosis: {
        finalDiagnosis: 'Neumonía atípica probable',
        reasoning: 'Tos seca, fatiga y evolución subaguda orientan a cuadro respiratorio compatible.'
      },
      messages: [
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
          content: '¿Desde cuándo comenzó con la tos?'
        },
        {
          id: 'm-03',
          role: 'PATIENT',
          timestamp: '10:32',
          content: 'Hace unos cinco días. Empeora por la noche.'
        }
      ]
    });
  }
}
