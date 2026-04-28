import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, forkJoin, map, of, throwError, timeout } from 'rxjs';

import { ClinicalCase } from '../models/clinical-case.model';
import { Simulation, SimulationStudent } from '../models/simulation.model';
import { environment } from '../../../environments/environment';

export interface SimulationAssignmentContext {
  clinicalCase: Pick<ClinicalCase, 'id' | 'title' | 'patientName' | 'reason'>;
  students: SimulationStudent[];
}

export interface CreateSimulationPayload {
  clinicalCaseId: string;
  courseId?: string;
  studentIds: string[];
  mode: 'TIME_LIMITED' | 'UNLIMITED';
  durationMinutes?: number;
  availability: 'IMMEDIATE' | 'SCHEDULED';
  availableAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class SimulationAssignmentService {
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly assignmentContextTimeoutMs = 12000;

  constructor(private http: HttpClient) {}

  getAssignmentContext(caseId: string): Observable<SimulationAssignmentContext> {
    if (environment.useMocks) {
      return of({
        clinicalCase: {
          id: caseId,
          title: 'Caso clínico',
          patientName: 'Paciente simulado',
          reason: 'Motivo no disponible'
        },
        students: []
      });
    }

    return forkJoin({
      clinicalCase: this.http
        .get<BackendClinicalCaseResponse>(`${this.apiBaseUrl}/clinical-cases/${caseId}`)
        .pipe(timeout(this.assignmentContextTimeoutMs)),
      students: this.http
        .get<BackendProfessorStudentResponse[]>(`${this.apiBaseUrl}/professor/students`)
        .pipe(timeout(this.assignmentContextTimeoutMs))
    }).pipe(
      map(({ clinicalCase, students }) => ({
        clinicalCase: {
          id: clinicalCase.id,
          title: clinicalCase.title,
          patientName: clinicalCase.patientName,
          reason: clinicalCase.chiefComplaint
        },
        students: students
          .map((student) => ({
            id: student.id,
            name: student.name,
            status: 'PENDING' as const,
            selected: true,
            canReview: false
          }))
      })),
      catchError(() => throwError(() => new Error('No fue posible cargar la lista de estudiantes.')))
    );
  }

  createSimulation(payload: CreateSimulationPayload): Observable<Simulation> {
    if (!environment.useMocks) {
      return this.http
        .post<BackendSimulationResponse>(`${this.apiBaseUrl}/simulations`, {
          clinicalCaseId: payload.clinicalCaseId,
          studentIds: payload.studentIds
        })
        .pipe(
          map((response) => ({
            id: response.activityId,
            name: 'Simulación asignada',
            clinicalCaseId: response.clinicalCaseId,
            clinicalCaseName: 'Caso clínico',
            courseName: 'Curso',
            mode: payload.mode,
            durationMinutes: payload.durationMinutes,
            availability: payload.availability,
            availableAt: payload.availableAt
          })),
          catchError((error: HttpErrorResponse) => {
            const backendMessage =
              (typeof error.error?.message === 'string' && error.error.message) ||
              (typeof error.error?.detail === 'string' && error.error.detail) ||
              error.message ||
              'No fue posible crear la simulación.';

            console.error('[SimulationAssignmentService] createSimulation failed', {
              status: error.status,
              clinicalCaseId: payload.clinicalCaseId,
              studentsCount: payload.studentIds.length,
              backendMessage
            });

            return throwError(() => new Error(backendMessage));
          })
        );
    }

    return of({
      id: 'sim-01',
      name: 'Entrevista respiratoria',
      clinicalCaseId: payload.clinicalCaseId,
      clinicalCaseName: 'Caso Catalina Paz Soto',
      courseName: 'Caso de prueba',
      mode: payload.mode,
      durationMinutes: payload.durationMinutes,
      availability: payload.availability,
      availableAt: payload.availableAt
    });
  }
}

interface BackendSimulationResponse {
  activityId: string;
  clinicalCaseId: string;
  assignedStudents: number;
}

interface BackendClinicalCaseResponse {
  id: string;
  title: string;
  patientName: string;
  chiefComplaint: string;
}

interface BackendProfessorStudentResponse {
  id: string;
  name: string;
  active: boolean;
}
