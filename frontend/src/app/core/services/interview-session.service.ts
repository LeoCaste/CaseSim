import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
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
      .pipe(
        map((messages) => messages.map((message) => this.mapBackendMessage(message))),
        catchError((error: unknown) => {
          const fallbackMessages = this.extractFallbackMessagesFromError(error);

          if (fallbackMessages.length > 0) {
            return of(fallbackMessages.map((message) => this.mapBackendMessage(message)));
          }

          return throwError(() => error);
        })
      );
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
    return this.http.get<BackendStudentActivityResponse[]>(`${this.apiBaseUrl}/student/activities`).pipe(
      switchMap((activities): Observable<ActivityContext> => {
        const activity = activities.find((item) => {
          const id = item.activityId ?? item.id;
          return id === activityId;
        });

        if (!activity) {
          return throwError(() => new Error('No se encontró la actividad seleccionada para la entrevista.'));
        }

        const activityContext: ActivityContext = {
          caseName: this.readFirstString(activity.title, activity.titulo) ?? 'Actividad clínica',
          patientName: this.readFirstString(activity.patientName, activity.nombrePaciente) ?? '',
          age: this.readFirstNumber(activity.patientAge, activity.edadPaciente) ?? 0,
          sex: this.normalizeSex(this.readFirstString(activity.patientSex, activity.sexoPaciente) ?? 'X'),
          context: this.readFirstString(activity.context, activity.descripcionContexto) ?? '',
          reason: this.readFirstString(activity.chiefComplaint, activity.motivoConsulta) ?? ''
        };

        return this.http.get<BackendStudentClinicalCaseContextResponse>(
          `${this.apiBaseUrl}/student/clinical-cases/${activityId}`
        ).pipe(
          map((clinicalCaseContext): ActivityContext => ({
            caseName:
              this.readFirstString(activityContext.caseName, clinicalCaseContext.title, clinicalCaseContext.titulo) ??
              'Actividad clínica',
            patientName:
              this.readFirstString(
                activityContext.patientName,
                clinicalCaseContext.patientName,
                clinicalCaseContext.nombrePaciente
              ) ?? '',
            age:
              this.readFirstNumber(
                activityContext.age > 0 ? activityContext.age : undefined,
                clinicalCaseContext.patientAge,
                clinicalCaseContext.edadPaciente
              ) ?? 0,
            sex: this.normalizeSex(
              this.readFirstString(
                activityContext.sex !== 'X' ? activityContext.sex : undefined,
                clinicalCaseContext.patientSex,
                clinicalCaseContext.sexoPaciente
              ) ?? 'X'
            ),
            context:
              this.readFirstString(
                activityContext.context,
                clinicalCaseContext.contextSummary,
                clinicalCaseContext.resumenContexto,
                clinicalCaseContext.encounterContext,
                clinicalCaseContext.contextoAtencion,
                clinicalCaseContext.clinicalSetting,
                clinicalCaseContext.ambitoClinico
              ) ?? '',
            reason:
              this.readFirstString(
                activityContext.reason,
                clinicalCaseContext.chiefComplaint,
                clinicalCaseContext.motivoConsulta
              ) ?? ''
          })),
          catchError(() =>
            throwError(
              () =>
                new Error(
                  'No fue posible cargar el contexto seguro de la entrevista desde /student/clinical-cases/{activityId}.'
                )
            )
          )
        );
      }),
      catchError((error) => throwError(() => error))
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

  private extractFallbackMessagesFromError(error: unknown): BackendChatMessageResponse[] {
    if (!(error instanceof HttpErrorResponse)) {
      return [];
    }

    const payload = error.error as BackendChatMessageResponse[] | { messages?: unknown; fallbackMessages?: unknown } | null | undefined;

    const directMessages = this.normalizeBackendMessages(payload);
    if (directMessages.length > 0) {
      return directMessages;
    }

    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return [];
    }

    const nestedMessages = this.normalizeBackendMessages(payload.messages);
    if (nestedMessages.length > 0) {
      return nestedMessages;
    }

    return this.normalizeBackendMessages(payload.fallbackMessages);
  }

  private normalizeBackendMessages(candidate: unknown): BackendChatMessageResponse[] {
    if (!Array.isArray(candidate)) {
      return [];
    }

    return candidate.filter((message): message is BackendChatMessageResponse => this.isBackendChatMessageResponse(message));
  }

  private isBackendChatMessageResponse(candidate: unknown): candidate is BackendChatMessageResponse {
    if (!candidate || typeof candidate !== 'object') {
      return false;
    }

    const message = candidate as Partial<BackendChatMessageResponse>;
    return typeof message.role === 'string' && typeof message.content === 'string' && typeof message.createdAt === 'string';
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

interface BackendStudentClinicalCaseContextResponse {
  title?: string;
  titulo?: string;
  patientName?: string;
  nombrePaciente?: string;
  patientAge?: number;
  edadPaciente?: number;
  patientSex?: string;
  sexoPaciente?: string;
  chiefComplaint?: string;
  motivoConsulta?: string;
  contextSummary?: string;
  resumenContexto?: string;
  encounterContext?: string;
  contextoAtencion?: string;
  clinicalSetting?: string;
  ambitoClinico?: string;
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
