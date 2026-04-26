export interface StudentActivity {
  id: string;
  title: string;
  course: string;
  professor: string;
  patient: string;
  status: string;
  statusType: 'success' | 'neutral' | 'warning' | 'danger';
  duration: string;
  description: string;
  actionLabel: string;
  route: string | null;
}

export interface SessionMessage {
  id: string;
  role: 'PATIENT' | 'STUDENT' | 'SYSTEM';
  content: string;
  timestamp: string;
}

export interface DiagnosisReview {
  finalDiagnosis: string;
  reasoning: string;
}

export interface StudentSession {
  id: string;
  title: string;
  patientName: string;
  status: 'IN_PROGRESS' | 'COMPLETED';
  submittedAt?: string;
  notes: string;
  hypothesis: string;
  diagnosis: DiagnosisReview;
  messages: SessionMessage[];
}
