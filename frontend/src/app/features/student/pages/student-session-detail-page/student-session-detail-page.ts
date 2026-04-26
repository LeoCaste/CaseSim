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
    this.studentSessionService.getStudentSessionDetail('sess-01').subscribe((session) => {
      this.title = session.title;
      this.messages = session.messages.map((message) => this.mapMessage(message));
      this.diagnosis = session.diagnosis;
      this.notes = session.notes;
      this.isLoading = false;
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
