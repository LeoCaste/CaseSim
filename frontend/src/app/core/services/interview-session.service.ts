import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, Observable, of, switchMap, tap } from 'rxjs';

import { DiagnosisReview, SessionMessage } from '../models/student-session.model';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

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
    private authService: AuthService
  ) {}

  getInterviewSession(): Observable<InterviewSessionData> {
    if (environment.useMocks) {
      return of(this.getMockInterviewSession());
    }

    const payload: CreateSessionRequest = {
      activityId: this.getCurrentActivityId() ?? environment.demoActivityId,
      studentId: this.authService.getCurrentUser()?.id ?? environment.demoStudentId
    };

    return this.http.post<BackendSessionResponse>(`${this.apiBaseUrl}/sessions`, payload).pipe(
      tap((session) => this.saveCurrentSessionId(session.id)),
      switchMap((session) =>
        forkJoin({
          session: this.http.get<BackendSessionResponse>(`${this.apiBaseUrl}/sessions/${session.id}`),
          messages: this.http.get<BackendChatMessageResponse[]>(`${this.apiBaseUrl}/sessions/${session.id}/messages`)
        })
      ),
      map(({ session, messages }) => ({
        ...this.getMockInterviewSession(),
        id: session.id,
        messages: messages.map((message) => this.mapBackendMessage(message))
      })),
      catchError(() => of(this.getMockInterviewSession()))
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
      return of<SessionMessage[]>([
        {
          id: `m-sys-${Date.now()}`,
          role: 'SYSTEM',
          timestamp: 'Ahora',
          content: 'No fue posible identificar la sesión activa.'
        }
      ]);
    }

    return this.http
      .post<BackendChatMessageResponse[]>(`${this.apiBaseUrl}/sessions/${resolvedSessionId}/messages`, {
        content
      })
      .pipe(
        map((messages) => messages.map((message) => this.mapBackendMessage(message))),
        catchError(() =>
          of<SessionMessage[]>([
            {
              id: `m-sys-${Date.now()}`,
              role: 'SYSTEM',
              timestamp: 'Ahora',
              content: 'No fue posible enviar el mensaje. Intenta nuevamente.'
            }
          ])
        )
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
        map(() => true),
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

interface CreateSessionRequest {
  activityId: string;
  studentId: string;
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
