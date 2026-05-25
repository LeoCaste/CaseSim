import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { ActivatedRoute } from '@angular/router';
import { SessionTranscript } from '../../components/session-transcript/session-transcript';
import { StudentNotebook } from '../../components/student-notebook/student-notebook';
import { EvaluationPanel } from '../../components/evaluation-panel/evaluation-panel';
import { DiagnosisReview, SessionMessage } from '../../../../core/models/student-session.model';
import { ProfessorSessionReview } from '../../../../core/models/professor-dashboard.model';
import { ProfessorDashboardService } from '../../../../core/services/professor-dashboard.service';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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

  transcript: Array<{ role: string; speakerType: 'PATIENT' | 'STUDENT' | 'SYSTEM'; time: string; content: string }> = [];

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
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private userContext: UserContext,
    private professorDashboardService: ProfessorDashboardService,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';
    const sessionId = this.route.snapshot.paramMap.get('id') ?? '';
    this.professorDashboardService
      .getProfessorSessionReview(sessionId)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (review) => {
          this.applyReview(review);
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadError = 'No fue posible cargar la sesión solicitada.';
          this.cdr.detectChanges();
        }
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

  private mapMessage(message: SessionMessage): { role: string; speakerType: 'PATIENT' | 'STUDENT' | 'SYSTEM'; time: string; content: string } {
    const speakerType = this.resolveSpeakerType(message.role);

    return {
      role: this.resolveSpeakerLabel(speakerType),
      speakerType,
      time: message.timestamp,
      content: message.content
    };
  }

  private resolveSpeakerType(role: string): 'PATIENT' | 'STUDENT' | 'SYSTEM' {
    const normalizedRole = role?.trim().toUpperCase();

    if (normalizedRole === 'PATIENT' || normalizedRole === 'ASSISTANT') {
      return 'PATIENT';
    }

    if (normalizedRole === 'STUDENT' || normalizedRole === 'USER') {
      return 'STUDENT';
    }

    return 'SYSTEM';
  }

  private resolveSpeakerLabel(speakerType: 'PATIENT' | 'STUDENT' | 'SYSTEM'): string {
    if (speakerType === 'STUDENT') {
      return this.session.student?.trim() || 'Estudiante';
    }

    if (speakerType === 'PATIENT') {
      return this.session.caseName?.trim() || 'Paciente simulado';
    }

    return 'Sistema';
  }
}
