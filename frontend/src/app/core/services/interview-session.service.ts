import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { DiagnosisReview, SessionMessage } from '../models/student-session.model';

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
  getInterviewSession(): Observable<InterviewSessionData> {
    return of({
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
    });
  }

  sendMessage(_sessionId: string, content: string): Observable<SessionMessage[]> {
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

  submitFinalDiagnosis(
    _sessionId: string,
    _diagnosis: DiagnosisReview,
    _notebook: InterviewNotebookState
  ): Observable<boolean> {
    return of(true);
  }
}
