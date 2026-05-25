import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, Observable, of, switchMap, tap, throwError } from 'rxjs';

import { DiagnosisReview, SessionMessage } from '../models/student-session.model';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { ProfessorDashboardService } from './professor-dashboard.service';

export interface InterviewSessionData {
  id: string;
  patientName: string;
  age: number;
  sex: 'F' | 'M' | 'X';
  context: string;
  reason: string;
  messages: SessionMessage[];
}

export interface InterviewNotebookState {
  notes: string;
  hypothesis: string;
}

@Injectable({
  providedIn: 'root'
})
export class InterviewSessionService {
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly currentSessionStorageKey = 'casesim.currentSessionId';
  private readonly currentActivityStorageKey = 'casesim.currentActivityId';

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private professorDashboardService: ProfessorDashboardService
  ) {}

  getInterviewSession(): Observable<InterviewSessionData> {
    if (environment.useMocks) {
      return of(this.getMockInterviewSession());
    }

    const activityId = this.getCurrentActivityId();
    if (!activityId) {
      return throwError(() => new Error('No se encontró una actividad seleccionada para iniciar la entrevista.'));
    }

    return this.http.post<BackendSessionResponse>(`${this.apiBaseUrl}/sessions`, { activityId }).pipe(
      tap((session) => this.saveCurrentSessionId(session.id)),
      switchMap((session) =>
        forkJoin({
          activityContext: this.getCurrentActivityContext(activityId),
          session: this.http.get<BackendSessionResponse>(`${this.apiBaseUrl}/sessions/${session.id}`),
          messages: this.http.get<BackendChatMessageResponse[]>(`${this.apiBaseUrl}/sessions/${session.id}/messages`)
        })
      ),
      tap(({ session, activityContext }) => {
        this.professorDashboardService.saveSessionSummary({
          sessionId: session.id,
          studentName: this.authService.getCurrentUser()?.fullName ?? 'Estudiante',
          caseName: activityContext.caseName,
          status: 'IN_PROGRESS',
          submittedAt: new Date().toISOString()
        });
      }),
      map(({ session, messages, activityContext }) =>
        this.mapBackendSessionToInterviewSession(session, messages, activityContext)
      ),
      catchError(() => throwError(() => new Error('No fue posible iniciar la entrevista.')))
    );
  }

  sendMessage(sessionId: string, content: string): Observable<SessionMessage[]> {
    if (environment.useMocks) {
      return of([
        {
          id: `m-stu-${Date.now()}`,
          role: 'STUDENT',
          timestamp: 'Ahora',
          content
        },
        {
          id: `m-pat-${Date.now()}`,
          role: 'PATIENT',
          timestamp: 'Ahora',
          content: 'Entiendo. Me pasa principalmente en la noche y me he sentido más cansada de lo normal.'
        }
      ]);
    }

    const resolvedSessionId = this.resolveSessionId(sessionId);

    if (!resolvedSessionId) {
      return throwError(() => new Error('No fue posible identificar la sesión activa.'));
    }

    return this.http
      .post<BackendChatMessageResponse[]>(`${this.apiBaseUrl}/sessions/${resolvedSessionId}/messages`, {
        content
      })
      .pipe(map((messages) => messages.map((message) => this.mapBackendMessage(message))));
  }

  submitFinalDiagnosis(
    sessionId: string,
    diagnosis: DiagnosisReview,
    _notebook: InterviewNotebookState
  ): Observable<boolean> {
    if (environment.useMocks) {
      return of(true);
    }

    const resolvedSessionId = this.resolveSessionId(sessionId);

    if (!resolvedSessionId) {
      return of(false);
    }

    return this.http
      .post<BackendSessionResponse>(`${this.apiBaseUrl}/sessions/${resolvedSessionId}/final-diagnosis`, {
        diagnosis: diagnosis.finalDiagnosis,
        reasoning: diagnosis.reasoning
      })
      .pipe(
        map(() => {
          this.professorDashboardService.saveSessionSummary({
            sessionId: resolvedSessionId,
            studentName: this.authService.getCurrentUser()?.fullName ?? 'Estudiante',
            caseName: 'Caso clínico',
            status: 'COMPLETED',
            submittedAt: new Date().toISOString()
          });
          return true;
        }),
        catchError(() => of(false))
      );
  }

  private getMockInterviewSession(): InterviewSessionData {
    return {
      id: 'sess-01',
      patientName: 'Catalina Paz Soto',
      age: 22,
      sex: 'F',
      context: 'Consulta ambulatoria',
      reason: 'tos seca y fatiga de 5 días',
      messages: [
        {
          id: 'm-01',
          role: 'PATIENT',
          timestamp: '6:00 AM',
          content: 'Vengo por tos seca y agotamiento desde hace cinco días.'
        },
        {
          id: 'm-02',
          role: 'STUDENT',
          timestamp: '6:01 AM',
          content: 'Gracias por comentarlo. ¿Desde cuándo nota que la tos empeora?'
        },
        {
          id: 'm-03',
          role: 'PATIENT',
          timestamp: '6:02 AM',
          content: 'Empeora en la noche y me cuesta dormir bien.'
        }
      ]
    };
  }

  private mapBackendMessage(message: BackendChatMessageResponse): SessionMessage {
    return {
      id: message.id,
      role: this.mapBackendRole(message.role),
      content: message.content,
      timestamp: this.formatTimestamp(message.createdAt)
    };
  }

  private mapBackendSessionToInterviewSession(
    session: BackendSessionResponse,
    messages: BackendChatMessageResponse[],
    activityContext: ActivityContext
  ): InterviewSessionData {
    return {
      id: session.id,
      patientName: activityContext.patientName,
      age: activityContext.age,
      sex: activityContext.sex,
      context: activityContext.context,
      reason: activityContext.reason,
      messages: messages.map((message) => this.mapBackendMessage(message))
    };
  }

  private getCurrentActivityContext(activityId: string): Observable<ActivityContext> {
    const fallbackContext: ActivityContext = {
      caseName: 'Caso clínico',
      patientName: 'Paciente simulado',
      age: 0,
      sex: this.normalizeSex('X'),
      context: '',
      reason: ''
    };

    return this.http.get<BackendStudentActivityResponse[]>(`${this.apiBaseUrl}/student/activities`).pipe(
      switchMap((activities): Observable<ActivityContext> => {
        const activity = activities.find((item) => {
          const id = item.activityId ?? item.id;
          return id === activityId;
        });

        const fallbackFromActivity: ActivityContext = {
          caseName: this.readFirstString(activity?.title, activity?.titulo) ?? 'Caso clínico',
          patientName: this.readFirstString(activity?.patientName, activity?.nombrePaciente) ?? 'Paciente simulado',
          age: this.readFirstNumber(activity?.patientAge, activity?.edadPaciente) ?? 0,
          sex: this.normalizeSex(this.readFirstString(activity?.patientSex, activity?.sexoPaciente) ?? 'X'),
          context: this.readFirstString(activity?.context, activity?.descripcionContexto) ?? '',
          reason:
            this.readFirstString(activity?.chiefComplaint, activity?.motivoConsulta, activity?.description, activity?.descripcion) ??
            ''
        };

        const clinicalCaseId = this.readFirstString(activity?.clinicalCaseId, activity?.casoClinicoId);

        if (!clinicalCaseId) {
          return of(fallbackFromActivity);
        }

        return this.http.get<BackendClinicalCaseResponse>(`${this.apiBaseUrl}/clinical-cases/${clinicalCaseId}`).pipe(
          map((clinicalCase): ActivityContext => ({
            caseName: this.readFirstString(activity?.title, activity?.titulo, clinicalCase.title) ?? 'Caso clínico',
            patientName:
              this.readFirstString(activity?.patientName, activity?.nombrePaciente, clinicalCase.patientName) ??
              'Paciente simulado',
            age: this.readFirstNumber(activity?.patientAge, activity?.edadPaciente, clinicalCase.patientAge) ?? 0,
            sex: this.normalizeSex(
              this.readFirstString(activity?.patientSex, activity?.sexoPaciente, clinicalCase.patientSex) ?? 'X'
            ),
            context: this.readFirstString(activity?.context, activity?.descripcionContexto, clinicalCase.description) ?? '',
            reason:
              this.readFirstString(
                activity?.chiefComplaint,
                activity?.motivoConsulta,
                activity?.description,
                activity?.descripcion,
                clinicalCase.chiefComplaint
              ) ?? ''
          })),
          catchError(() => of(fallbackFromActivity))
        );
      }),
      catchError(() => of(fallbackContext))
    );
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

  private normalizeSex(value: unknown): PatientSex {
    return value === 'F' || value === 'M' || value === 'X' ? value : 'X';
  }

  private mapBackendRole(role: string): 'PATIENT' | 'STUDENT' | 'SYSTEM' {
    if (role === 'ASSISTANT') return 'PATIENT';
    if (role === 'USER') return 'STUDENT';
    return 'SYSTEM';
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

  private saveCurrentSessionId(sessionId: string): void {
    if (typeof window === 'undefined') {
      return;
    }

    window.localStorage.setItem(this.currentSessionStorageKey, sessionId);
  }

  private getCurrentSessionId(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }

    return window.localStorage.getItem(this.currentSessionStorageKey);
  }

  private getCurrentActivityId(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }

    const activityId = window.localStorage.getItem(this.currentActivityStorageKey);
    return activityId && this.looksLikeUuid(activityId) ? activityId : null;
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

interface BackendSessionResponse {
  id: string;
  activityId: string;
  studentId: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  createdAt: string;
}

interface BackendChatMessageResponse {
  id: string;
  sessionId: string;
  role: string;
  content: string;
  turnNumber: number;
  createdAt: string;
}

interface BackendStudentActivityResponse {
  activityId?: string;
  id?: string;
  clinicalCaseId?: string;
  casoClinicoId?: string;
  title?: string;
  titulo?: string;
  description?: string;
  descripcion?: string;
  patientName?: string;
  nombrePaciente?: string;
  patientAge?: number;
  edadPaciente?: number;
  patientSex?: string;
  sexoPaciente?: string;
  chiefComplaint?: string;
  motivoConsulta?: string;
  context?: string;
  descripcionContexto?: string;
}

interface BackendClinicalCaseResponse {
  id: string;
  title?: string;
  description?: string;
  patientName?: string;
  patientAge?: number;
  patientSex?: string;
  chiefComplaint?: string;
}

interface ActivityContext {
  caseName: string;
  patientName: string;
  age: number;
  sex: 'F' | 'M' | 'X';
  context: string;
  reason: string;
}

type PatientSex = ActivityContext['sex'];
