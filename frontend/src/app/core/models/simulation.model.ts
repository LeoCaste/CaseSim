export interface Simulation {
  id: string;
  name: string;
  clinicalCaseId: string;
  clinicalCaseName: string;
  courseName: string;
  mode: 'TIME_LIMITED' | 'UNLIMITED';
  durationMinutes?: number;
  availability: 'IMMEDIATE' | 'SCHEDULED';
  availableAt?: string;
}

export interface SimulationStudent {
  id: string;
  name: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  selected?: boolean;
  canReview?: boolean;
}
