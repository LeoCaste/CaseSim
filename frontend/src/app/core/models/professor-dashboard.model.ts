import { DiagnosisReview, SessionMessage } from './student-session.model';

export interface ProfessorSummaryItem {
  label: string;
  value: string;
}

export interface ProfessorSimulationOverview {
  name: string;
  caseName: string;
  course: string;
  students: number;
  completedSessions: number;
}

export interface ProfessorActivityOverview {
  title: string;
  course: string;
  caseName: string;
  status: string;
  completed: number;
  total: number;
}

export interface ProfessorRecentSession {
  id: string;
  student: string;
  activity: string;
  status: string;
  turns: number;
  duration: string;
}

export interface ProfessorDashboard {
  summary: ProfessorSummaryItem[];
  simulations: ProfessorSimulationOverview[];
  activities: ProfessorActivityOverview[];
  sessions: ProfessorRecentSession[];
}

export interface ProfessorSessionReview {
  session: {
    id: string;
    studentName: string;
    activityName: string;
    caseName: string;
    status: 'IN_PROGRESS' | 'COMPLETED';
    durationMinutes: number;
    turns: number;
    submittedAt?: string;
  };
  transcript: SessionMessage[];
  notebook: {
    notes: string;
    hypothesis: string;
  };
  diagnosis: DiagnosisReview;
}
