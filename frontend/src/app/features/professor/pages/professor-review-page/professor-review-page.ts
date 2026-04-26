import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { SessionTranscript } from '../../components/session-transcript/session-transcript';
import { StudentNotebook } from '../../components/student-notebook/student-notebook';
import { EvaluationPanel } from '../../components/evaluation-panel/evaluation-panel';
import { DiagnosisReview, SessionMessage } from '../../../../core/models/student-session.model';
import { ProfessorSessionReview } from '../../../../core/models/professor-dashboard.model';
import { ProfessorDashboardService } from '../../../../core/services/professor-dashboard.service';

@Component({
  selector: 'app-professor-review-page',
  imports: [CommonModule, SessionTranscript, StudentNotebook, EvaluationPanel],
  templateUrl: './professor-review-page.html',
  styleUrl: './professor-review-page.css',
})
export class ProfessorReviewPage implements OnInit {
  session = {
    id: 'sess-01',
    student: 'Diego Muñoz',
    activity: 'Entrevista respiratoria',
    caseName: 'Catalina Paz Soto',
    status: 'Completada',
    duration: '12 min',
    turns: 18,
    submittedAt: '25/04/2026 · 10:42'
  };

  transcript: Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }> = [];

  notebook = {
    notes:
      'Paciente consulta por tos seca persistente y fatiga. Refiere empeoramiento nocturno y alteración del sueño. Pendiente explorar antecedentes respiratorios y contactos.',
    hypothesis: 'Cuadro respiratorio subagudo. Considerar infección respiratoria atípica como hipótesis inicial.'
  };

  diagnosis: DiagnosisReview = {
    finalDiagnosis: 'Neumonía atípica probable',
    reasoning:
      'Tos seca de varios días, fatiga, fiebre baja y evolución subaguda orientan a cuadro respiratorio compatible con neumonía atípica.'
  };
  isLoading = false;
  loadError = '';

  constructor(
    private userContext: UserContext,
    private professorDashboardService: ProfessorDashboardService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.professorDashboardService.getProfessorSessionReview('sess-01').subscribe((review) => {
      this.applyReview(review);
      this.isLoading = false;
    });
  }

  private applyReview(review: ProfessorSessionReview): void {
    this.session = {
      id: review.session.id,
      student: review.session.studentName,
      activity: review.session.activityName,
      caseName: review.session.caseName,
      status: review.session.status === 'COMPLETED' ? 'Completada' : 'En curso',
      duration: `${review.session.durationMinutes} min`,
      turns: review.session.turns,
      submittedAt: review.session.submittedAt ?? ''
    };
    this.transcript = review.transcript.map((message) => this.mapMessage(message));
    this.notebook = review.notebook;
    this.diagnosis = review.diagnosis;
  }

  private mapMessage(message: SessionMessage): { role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string } {
    return {
      role: message.role === 'PATIENT' ? 'Paciente' : message.role === 'STUDENT' ? 'Estudiante' : 'Sistema',
      time: message.timestamp,
      content: message.content
    };
  }
}
