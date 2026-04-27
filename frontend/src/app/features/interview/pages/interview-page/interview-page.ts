import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
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
  @ViewChild('conversationPanel') private conversationPanel?: ElementRef<HTMLElement>;

  showFinalDiagnosisModal = false;
  clinicalIntervention = '';
  clinicalNotes = '';
  diagnosticHypothesis = '';
  finalDiagnosis = '';
  finalReasoning = '';
  session: InterviewSessionData = {
    id: '',
    patientName: 'Catalina Paz Soto',
    age: 22,
    sex: 'F',
    context: 'Consulta ambulatoria',
    reason: 'tos seca y fatiga de 5 días',
    messages: []
  };
  isLoading = false;
  isSendingMessage = false;
  isSubmittingFinalDiagnosis = false;
  loadError = '';
  finalDiagnosisError = '';

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
    this.loadError = '';

    this.interviewSessionService.getInterviewSession().subscribe({
      next: (session) => {
        this.session = session;
        this.messages = session.messages.map((message) => this.mapMessage(message));
        this.isLoading = false;
        this.scrollConversationToBottom();
      },
      error: () => {
        this.isLoading = false;
        this.loadError = 'No fue posible cargar la entrevista. Recarga la página para reintentar.';
      }
    });
  }

  sendIntervention(): void {
    const content = this.clinicalIntervention.trim();

    if (!content || this.isSendingMessage || this.isSubmittingFinalDiagnosis) {
      return;
    }

    this.isSendingMessage = true;

    this.interviewSessionService.sendMessage(this.session.id, content).subscribe({
      next: (newMessages) => {
        this.messages = [...this.messages, ...newMessages.map((message) => this.mapMessage(message))];
        this.clinicalIntervention = '';
        this.isSendingMessage = false;
        this.scrollConversationToBottom();
      },
      error: () => {
        this.isSendingMessage = false;
        this.loadError = 'No fue posible enviar el mensaje. Intenta nuevamente.';
      }
    });
  }

  onInterventionKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
      return;
    }

    event.preventDefault();
    this.sendIntervention();
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
    this.finalDiagnosisError = '';

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

    const diagnosisReview: DiagnosisReview = {
      finalDiagnosis: payload.finalDiagnosis.diagnosis,
      reasoning: payload.finalDiagnosis.reasoning
    };

    this.isSubmittingFinalDiagnosis = true;

    this.interviewSessionService
      .submitFinalDiagnosis(this.session.id, diagnosisReview, payload.notebook)
      .subscribe({
        next: (success) => {
          this.isSubmittingFinalDiagnosis = false;

          if (!success) {
            this.finalDiagnosisError = 'No fue posible enviar el diagnóstico final. Intenta nuevamente.';
            return;
          }

          this.router.navigate(['/session-completed']);
        },
        error: () => {
          this.isSubmittingFinalDiagnosis = false;
          this.finalDiagnosisError = 'No fue posible enviar el diagnóstico final. Intenta nuevamente.';
        }
      });
  }

  private mapMessage(message: SessionMessage): { role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string } {
    return {
      role: message.role === 'PATIENT' ? 'Paciente' : message.role === 'STUDENT' ? 'Estudiante' : 'Sistema',
      time: message.timestamp,
      content: message.content
    };
  }

  private scrollConversationToBottom(): void {
    requestAnimationFrame(() => {
      const panel = this.conversationPanel?.nativeElement;

      if (!panel) {
        return;
      }

      panel.scrollTop = panel.scrollHeight;
    });
  }
}
