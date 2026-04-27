import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, map, Observable, of, throwError } from 'rxjs';

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
  private readonly currentActivityStorageKey = 'casesim.currentActivityId';

  constructor(private http: HttpClient) {}

  getActivities(): Observable<StudentActivity[]> {
    if (environment.useMocks) {
      return of(this.getMockDashboardData().activities);
    }

    return this.http
      .get<BackendStudentActivityResponse[]>(`${this.apiBaseUrl}/student/activities`)
      .pipe(map((activities) => activities.map((activity) => this.mapBackendActivityToFrontend(activity))));
  }

  getDashboardData(): Observable<StudentDashboardData> {
    return this.getActivities().pipe(
      map((activities) => ({
        activities,
        history: this.getCurrentSessionId()
          ? [
              {
                title: 'Entrevista clínica reciente',
                patient: activities[0]?.patient ?? 'Paciente simulado',
                status: 'Registrada',
                date: new Date().toLocaleDateString('es-CL'),
                route: '/student/session-detail'
              }
            ]
          : []
      }))
    );
  }

  setCurrentActivityId(activityId: string): void {
    if (typeof window === 'undefined' || !this.looksLikeUuid(activityId)) {
      return;
    }

    window.localStorage.setItem(this.currentActivityStorageKey, activityId);
  }

  getCurrentActivityId(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }

    const activityId = window.localStorage.getItem(this.currentActivityStorageKey);
    return activityId && this.looksLikeUuid(activityId) ? activityId : null;
  }

  getStudentSessionDetail(sessionId: string): Observable<StudentSession> {
    if (environment.useMocks) {
      return of(this.getMockStudentSession());
    }

    const resolvedSessionId = this.resolveSessionId(sessionId);
    if (!resolvedSessionId) {
      return throwError(() => new Error('No hay una sesión activa para mostrar detalle.'));
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
            finalDiagnosis: session.finalDiagnosis ?? '',
            reasoning: session.finalReasoning ?? ''
          },
          messages: messages.map((message) => this.mapBackendMessage(message))
        };
      })
    );
  }

  getCurrentSessionId(): string | null {
    return this.readCurrentSessionId();
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

  private mapBackendActivityToFrontend(activity: BackendStudentActivityResponse): StudentActivity {
    const statusRaw = this.readFirstString(
      activity.status,
      activity.estado,
      activity.sessionStatus,
      activity.estadoSesion,
      activity.assignmentStatus,
      activity.estadoAsignacion
    );
    const isAvailable = this.resolveIsAvailable(statusRaw);

    const durationMinutes = this.readFirstNumber(activity.durationMinutes, activity.tiempoLimiteMinutos);

    const title = this.readFirstString(activity.title, activity.titulo) ?? 'Actividad clínica';
    const description =
      this.readFirstString(activity.description, activity.descripcion) ??
      'Realiza una anamnesis clínica y concluye cuando tengas una hipótesis diagnóstica.';

    const course = this.readFirstString(activity.courseName, activity.nombreCurso) ?? 'Curso';
    const professor = this.readFirstString(activity.professorName, activity.nombreProfesor) ?? 'Equipo docente';
    const patient = this.readFirstString(activity.patientName, activity.nombrePaciente) ?? 'Paciente simulado';

    const status =
      this.readFirstString(activity.statusLabel, activity.estadoLabel) ?? (isAvailable ? 'Disponible' : 'Pendiente');

    const actionLabel = this.readFirstString(activity.actionLabel, activity.etiquetaAccion) ?? 'Iniciar entrevista';

    const duration =
      this.readFirstString(activity.durationLabel, activity.duracionLabel) ??
      (durationMinutes && durationMinutes > 0 ? `${durationMinutes} minutos` : 'Sin límite de tiempo');

    const activityId = this.readFirstString(activity.activityId, activity.id);
    const hasValidActivityId = !!activityId && this.looksLikeUuid(activityId);
    const canStart = isAvailable && hasValidActivityId;

    return {
      id: activityId ?? '',
      title,
      course,
      professor,
      patient,
      status,
      statusType: canStart ? 'success' : 'neutral',
      duration,
      description,
      actionLabel: canStart ? actionLabel : 'No disponible',
      route: canStart ? '/student/waiting-room' : null
    };
  }

  private resolveIsAvailable(statusRaw: string | null): boolean {
    if (!statusRaw) {
      return true;
    }

    const normalized = statusRaw.toUpperCase();
    return ['AVAILABLE', 'DISPONIBLE', 'PENDIENTE', 'EN_CURSO', 'IN_PROGRESS'].includes(normalized);
  }

  private readFirstString(...values: Array<string | null | undefined>): string | null {
    for (const value of values) {
      if (typeof value === 'string' && value.trim().length > 0) {
        return value;
      }
    }

    return null;
  }

  private readFirstNumber(...values: Array<number | null | undefined>): number | null {
    for (const value of values) {
      if (typeof value === 'number' && Number.isFinite(value)) {
        return value;
      }
    }

    return null;
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

  private readCurrentSessionId(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }

    return window.localStorage.getItem(this.currentSessionStorageKey);
  }

  private resolveSessionId(sessionId: string): string | null {
    if (this.looksLikeUuid(sessionId)) {
      return sessionId;
    }

    return this.readCurrentSessionId();
  }

  private looksLikeUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }
}

interface BackendStudentActivityResponse {
  activityId?: string;
  id?: string;
  title?: string;
  titulo?: string;
  description?: string;
  descripcion?: string;
  patientName?: string;
  nombrePaciente?: string;
  courseName?: string;
  nombreCurso?: string;
  professorName?: string;
  nombreProfesor?: string;
  status?: string;
  estado?: string;
  sessionStatus?: string;
  estadoSesion?: string;
  assignmentStatus?: string;
  estadoAsignacion?: string;
  statusLabel?: string;
  estadoLabel?: string;
  actionLabel?: string;
  etiquetaAccion?: string;
  durationMinutes?: number;
  tiempoLimiteMinutos?: number;
  durationLabel?: string;
  duracionLabel?: string;
}

interface BackendSessionResponse {
  id: string;
  status: string;
  finishedAt: string | null;
  finalDiagnosis?: string | null;
  finalReasoning?: string | null;
}

interface BackendChatMessageResponse {
  id: string;
  role: string;
  content: string;
  createdAt: string;
}
