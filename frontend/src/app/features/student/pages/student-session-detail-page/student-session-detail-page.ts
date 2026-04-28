import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { DiagnosisReview, SessionMessage } from '../../../../core/models/student-session.model';
import { StudentSessionService } from '../../../../core/services/student-session.service';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private userContext: UserContext,
    private studentSessionService: StudentSessionService,
    private cdr: ChangeDetectorRef
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
      this.cdr.detectChanges();
      return;
    }

    this.studentSessionService
      .getStudentSessionDetail(sessionId)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (session) => {
          this.title = session.title;
          this.messages = session.messages.map((message) => this.mapMessage(message));
          this.diagnosis = session.diagnosis;
          this.notes = session.notes;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadError = 'No fue posible cargar el detalle de la sesión.';
          this.cdr.detectChanges();
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
