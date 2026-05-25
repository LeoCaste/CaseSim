import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable, of, throwError, timeout } from 'rxjs';

import { ProfessorDashboard, ProfessorSessionReview } from '../models/professor-dashboard.model';
import { environment } from '../../../environments/environment';

interface StoredSessionSummary {
  sessionId: string;
  studentName: string;
  caseName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  submittedAt: string;
}

@Injectable({ providedIn: 'root' })
export class ProfessorDashboardService {
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly dashboardRequestTimeoutMs = 12000;

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<ProfessorDashboard> {
    if (environment.useMocks) {
      return of(this.getMockDashboard());
    }

    return this.http.get<BackendProfessorSessionSummary[]>(`${this.apiBaseUrl}/professor/sessions`).pipe(
      timeout(this.dashboardRequestTimeoutMs),
      map((sessions) => ({
        summary: [
          { label: 'Sesiones revisables', value: String(sessions.length) },
          { label: 'Sesiones completadas', value: String(sessions.filter((item) => item.status === 'COMPLETED').length) }
        ],
        simulations: [],
        activities: [],
        sessions: sessions.map((item) => ({
          id: item.id,
          student: item.studentName,
          caseName: item.caseName,
          status: item.status === 'COMPLETED' ? 'Completada' : 'En curso',
          submittedAt: this.formatSubmittedAt(item.submittedAt)
        }))
      })),
      catchError(() => throwError(() => new Error('No fue posible cargar el panel docente.')))
    );
  }

  getProfessorSessionReview(sessionId: string): Observable<ProfessorSessionReview> {
    if (environment.useMocks) {
      return of(this.getMockReview());
    }

    return this.http
      .get<ProfessorSessionReview>(`${this.apiBaseUrl}/professor/sessions/${sessionId}`)
      .pipe(catchError(() => throwError(() => new Error('No fue posible cargar la revisión de la sesión.'))));
  }

  saveEvaluation(_sessionId: string, _feedback: string): Observable<boolean> {
    return of(true);
  }

  saveSessionSummary(_summary: StoredSessionSummary): void {
    // compatibilidad con flujo frontend existente
  }

  private getMockDashboard(): ProfessorDashboard {
    return {
      summary: [
        { label: 'Sesiones revisables', value: '2' },
        { label: 'Sesiones completadas', value: '1' }
      ],
      simulations: [],
      activities: [],
      sessions: [
        { id: 'sess-01', student: 'Diego Muñoz', caseName: 'Catalina Paz Soto', status: 'Completada', submittedAt: '25/04/2026 · 10:42' },
        { id: 'sess-02', student: 'Valentina Ríos', caseName: 'Marco Díaz Leiva', status: 'En curso', submittedAt: '—' }
      ]
    };
  }

  private formatSubmittedAt(submittedAt?: string | null): string {
    if (!submittedAt) {
      return '—';
    }

    return submittedAt;
  }

  private getMockReview(): ProfessorSessionReview {
    return {
      session: {
        id: 'sess-01',
        studentName: 'Diego Muñoz',
        activityName: 'Entrevista clínica',
        caseName: 'Catalina Paz Soto',
        status: 'COMPLETED',
        durationMinutes: 12,
        turns: 18,
        submittedAt: '25/04/2026'
      },
      transcript: [],
      notebook: { notes: '', hypothesis: '' },
      diagnosis: { finalDiagnosis: 'Diagnóstico de ejemplo', reasoning: 'Razonamiento de ejemplo' }
    };
  }
}

interface BackendProfessorSessionSummary {
  id: string;
  studentName: string;
  caseName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  submittedAt?: string | null;
}
