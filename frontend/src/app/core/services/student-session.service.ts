import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, Observable, of } from 'rxjs';

import { StudentActivity, StudentSession } from '../models/student-session.model';
import { environment } from '../../../environments/environment';

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
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly currentSessionStorageKey = 'casesim.currentSessionId';

  constructor(private http: HttpClient) {}

  getDashboardData(): Observable<StudentDashboardData> {
    if (environment.useMocks) {
      return of(this.getMockDashboardData());
    }

    return this.http
      .get<BackendClinicalCaseResponse[]>(`${this.apiBaseUrl}/clinical-cases`)
      .pipe(
        map((clinicalCases) => ({
          activities: clinicalCases.map((item, index) => this.mapBackendCaseToActivity(item, index)),
          history: this.getCurrentSessionId()
            ? [
                {
                  title: 'Entrevista clínica reciente',
                  patient: clinicalCases[0]?.patientName ?? 'Paciente simulado',
                  status: 'Registrada',
                  date: new Date().toLocaleDateString('es-CL'),
                  route: '/student/session-detail'
                }
              ]
            : []
        })),
        catchError(() => of(this.getMockDashboardData()))
      );
  }

  getStudentSessionDetail(sessionId: string): Observable<StudentSession> {
    if (environment.useMocks) {
      return of(this.getMockStudentSession());
    }

    const resolvedSessionId = this.resolveSessionId(sessionId);
    if (!resolvedSessionId) {
      return of(this.getMockStudentSession());
    }

    return forkJoin({
      session: this.http.get<BackendSessionResponse>(`${this.apiBaseUrl}/sessions/${resolvedSessionId}`),
      messages: this.http.get<BackendChatMessageResponse[]>(`${this.apiBaseUrl}/sessions/${resolvedSessionId}/messages`)
    }).pipe(
      map(({ session, messages }) => {
        const status: StudentSession['status'] = session.status === 'FINALIZADA' ? 'COMPLETED' : 'IN_PROGRESS';

        return {
          id: session.id,
          title: `Sesión ${session.id.slice(0, 8)}`,
          patientName: 'Paciente simulado',
          status,
          submittedAt: session.finishedAt ?? undefined,
          notes: '',
          hypothesis: '',
          diagnosis: {
            finalDiagnosis: '',
            reasoning: ''
          },
          messages: messages.map((message) => this.mapBackendMessage(message))
        };
      }),
      catchError(() => of(this.getMockStudentSession()))
    );
  }

  private getMockDashboardData(): StudentDashboardData {
    return {
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
    };
  }

  private getMockStudentSession(): StudentSession {
    return {
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
    };
  }

  private mapBackendCaseToActivity(
    clinicalCase: BackendClinicalCaseResponse,
    index: number
  ): StudentActivity {
    return {
      id: clinicalCase.id,
      title: clinicalCase.title,
      course: 'Caso de prueba',
      professor: 'Equipo docente',
      patient: clinicalCase.patientName,
      status: 'Disponible',
      statusType: 'success',
      duration: 'Sin límite de tiempo',
      description: clinicalCase.description || 'Simulación clínica disponible.',
      actionLabel: 'Iniciar entrevista',
      route: index === 0 ? '/student/waiting-room' : '/student/waiting-room'
    };
  }

  private mapBackendMessage(message: BackendChatMessageResponse): {
    id: string;
    role: 'PATIENT' | 'STUDENT' | 'SYSTEM';
    content: string;
    timestamp: string;
  } {
    return {
      id: message.id,
      role: message.role === 'ASSISTANT' ? 'PATIENT' : message.role === 'USER' ? 'STUDENT' : 'SYSTEM',
      content: message.content,
      timestamp: this.formatTimestamp(message.createdAt)
    };
  }

  private formatTimestamp(rawDate: string): string {
    const parsedDate = new Date(rawDate);
    if (Number.isNaN(parsedDate.getTime())) {
      return 'Ahora';
    }

    return parsedDate.toLocaleTimeString('es-CL', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  private getCurrentSessionId(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }

    return window.localStorage.getItem(this.currentSessionStorageKey);
  }

  private resolveSessionId(sessionId: string): string | null {
    if (this.looksLikeUuid(sessionId)) {
      return sessionId;
    }

    return this.getCurrentSessionId();
  }

  private looksLikeUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }
}

interface BackendClinicalCaseResponse {
  id: string;
  title: string;
  description: string;
  patientName: string;
}

interface BackendSessionResponse {
  id: string;
  status: string;
  finishedAt: string | null;
}

interface BackendChatMessageResponse {
  id: string;
  role: string;
  content: string;
  createdAt: string;
}
