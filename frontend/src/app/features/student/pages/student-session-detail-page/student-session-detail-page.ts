import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { DiagnosisReview, SessionMessage } from '../../../../core/models/student-session.model';
import { StudentSessionService } from '../../../../core/services/student-session.service';

@Component({
  selector: 'app-student-session-detail-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './student-session-detail-page.html',
  styleUrl: './student-session-detail-page.css'
})
export class StudentSessionDetailPage implements OnInit {
  title = 'Caso Catalina Paz Soto';
  messages: Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }> = [];
  diagnosis: DiagnosisReview = { finalDiagnosis: '', reasoning: '' };
  notes = '';
  isLoading = false;
  loadError = '';

  constructor(
    private userContext: UserContext,
    private studentSessionService: StudentSessionService
  ) {
    this.userContext.setRole('student');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';

    const sessionId = this.studentSessionService.getCurrentSessionId();
    if (!sessionId) {
      this.isLoading = false;
      this.loadError = 'No hay sesión reciente para mostrar.';
      return;
    }

    this.studentSessionService.getStudentSessionDetail(sessionId).subscribe({
      next: (session) => {
        this.title = session.title;
        this.messages = session.messages.map((message) => this.mapMessage(message));
        this.diagnosis = session.diagnosis;
        this.notes = session.notes;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.loadError = 'No fue posible cargar el detalle de la sesión.';
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
}
