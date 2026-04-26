import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { ClinicalCase } from '../models/clinical-case.model';
import { Simulation, SimulationStudent } from '../models/simulation.model';

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
  getAssignmentContext(caseId: string): Observable<SimulationAssignmentContext> {
    return of({
      clinicalCase: {
        id: caseId,
        title: 'Caso Catalina Paz Soto',
        patientName: 'Catalina Paz Soto',
        reason: 'tos seca y fatiga'
      },
      students: [
        { id: 'stu-01', name: 'Diego Muñoz', status: 'COMPLETED', selected: true, canReview: true },
        { id: 'stu-02', name: 'Valentina Ríos', status: 'IN_PROGRESS', selected: true, canReview: false },
        { id: 'stu-03', name: 'Matías Soto', status: 'PENDING', selected: true, canReview: false },
        { id: 'stu-04', name: 'Isidora Vega', status: 'PENDING', selected: false, canReview: false }
      ]
    });
  }

  createSimulation(payload: CreateSimulationPayload): Observable<Simulation> {
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
