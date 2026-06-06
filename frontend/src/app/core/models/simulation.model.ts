export interface Simulation {
  id: string;
  name: string;
  clinicalCaseId: string;
  clinicalCaseName: string;
  courseName: string;
}

export interface SimulationStudent {
  id: string;
  name: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  selected?: boolean;
  canReview?: boolean;
}
