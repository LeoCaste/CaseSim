import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserContext } from '../../../../core/services/user-context';
import { FinalDiagnosisModal } from '../../components/final-diagnosis-modal/final-diagnosis-modal';
import {
  InterviewSessionData,
  InterviewSessionService
} from '../../../../core/services/interview-session.service';
import { DiagnosisReview, SessionMessage } from '../../../../core/models/student-session.model';

@Component({
  selector: 'app-interview-page',
  imports: [CommonModule, FormsModule, FinalDiagnosisModal],
  templateUrl: './interview-page.html',
  styleUrl: './interview-page.css'
})
export class InterviewPage implements OnInit {
  showFinalDiagnosisModal = false;
  clinicalIntervention = '';
  clinicalNotes = '';
  diagnosticHypothesis = '';
  finalDiagnosis = '';
  finalReasoning = '';
  session: InterviewSessionData = {
    id: 'sess-01',
    patientName: 'Catalina Paz Soto',
    age: 22,
    sex: 'F',
    context: 'Consulta ambulatoria',
    reason: 'tos seca y fatiga de 5 días',
    messages: []
  };
  isLoading = false;
  loadError = '';

  messages: Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }> = [];

  constructor(
    private userContext: UserContext,
    private router: Router,
    private interviewSessionService: InterviewSessionService
  ) {
    this.userContext.setRole('student');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.interviewSessionService.getInterviewSession().subscribe((session) => {
      this.session = session;
      this.messages = session.messages.map((message) => this.mapMessage(message));
      this.isLoading = false;
    });
  }

  sendIntervention(): void {
    const content = this.clinicalIntervention.trim();

    if (!content) {
      return;
    }

    this.interviewSessionService.sendMessage(this.session.id, content).subscribe((newMessages) => {
      this.messages = [...this.messages, ...newMessages.map((message) => this.mapMessage(message))];
      this.clinicalIntervention = '';
    });
  }

  openFinalDiagnosisModal(): void {
    this.finalDiagnosis = this.finalDiagnosis?.trim() ? this.finalDiagnosis : (this.diagnosticHypothesis || '');
    this.showFinalDiagnosisModal = true;
  }

  closeFinalDiagnosisModal(): void {
    this.showFinalDiagnosisModal = false;
  }

  confirmFinalDiagnosis(diagnosis: { diagnosis: string; reasoning: string }): void {
    this.showFinalDiagnosisModal = false;

    this.finalDiagnosis = diagnosis.diagnosis;
    this.finalReasoning = diagnosis.reasoning;

    const payload = {
      notebook: {
        notes: this.clinicalNotes,
        hypothesis: this.diagnosticHypothesis
      },
      finalDiagnosis: {
        diagnosis: this.finalDiagnosis,
        reasoning: this.finalReasoning
      }
    };

    console.log('Final diagnosis payload (mock):', payload);

    const diagnosisReview: DiagnosisReview = {
      finalDiagnosis: payload.finalDiagnosis.diagnosis,
      reasoning: payload.finalDiagnosis.reasoning
    };

    this.interviewSessionService
      .submitFinalDiagnosis(this.session.id, diagnosisReview, payload.notebook)
      .subscribe(() => {
        this.router.navigate(['/session-completed']);
      });
  }

  private mapMessage(message: SessionMessage): { role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string } {
    return {
      role: message.role === 'PATIENT' ? 'Paciente' : message.role === 'STUDENT' ? 'Estudiante' : 'Sistema',
      time: message.timestamp,
      content: message.content
    };
  }
}
