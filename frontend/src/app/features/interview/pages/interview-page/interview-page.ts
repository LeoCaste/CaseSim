import { ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
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
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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
    patientName: '',
    age: 0,
    sex: 'X',
    context: '',
    reason: '',
    messages: []
  };
  isLoading = false;
  isSendingMessage = false;
  isSubmittingFinalDiagnosis = false;
  loadError = '';
  finalDiagnosisError = '';
  private readonly destroyRef = inject(DestroyRef);

  messages: Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }> = [];

  constructor(
    private userContext: UserContext,
    private router: Router,
    private interviewSessionService: InterviewSessionService,
    private cdr: ChangeDetectorRef
  ) {
    this.userContext.setRole('student');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';

    this.interviewSessionService
      .getInterviewSession()
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
          this.session = session;
          this.messages = session.messages.map((message) => this.mapMessage(message));
          this.scrollConversationToBottom();
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadError = 'No fue posible cargar la entrevista. Recarga la página para reintentar.';
          this.cdr.detectChanges();
        }
      });
  }

  sendIntervention(): void {
    const content = this.clinicalIntervention.trim();

    if (!content || this.isLoading || !this.session.id || this.isSendingMessage || this.isSubmittingFinalDiagnosis) {
      return;
    }

    this.loadError = '';
    this.isSendingMessage = true;

    this.interviewSessionService
      .sendMessage(this.session.id, content)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSendingMessage = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (newMessages) => {
          const mappedNewMessages = newMessages.map((message) => this.mapMessage(message));
          this.messages = this.mergeMessagesWithoutDuplicates(this.messages, mappedNewMessages);
          this.clinicalIntervention = '';
          this.loadError = '';
          this.scrollConversationToBottom();
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadError = 'No fue posible enviar el mensaje. Intenta nuevamente.';
          this.cdr.detectChanges();
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
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSubmittingFinalDiagnosis = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (success) => {
          if (!success) {
            this.finalDiagnosisError = 'No fue posible enviar el diagnóstico final. Intenta nuevamente.';
            this.cdr.detectChanges();
            return;
          }

          this.cdr.detectChanges();
          this.router.navigate(['/session-completed']);
        },
        error: () => {
          this.finalDiagnosisError = 'No fue posible enviar el diagnóstico final. Intenta nuevamente.';
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

  private scrollConversationToBottom(): void {
    requestAnimationFrame(() => {
      const panel = this.conversationPanel?.nativeElement;

      if (!panel) {
        return;
      }

      panel.scrollTop = panel.scrollHeight;
    });
  }

  private mergeMessagesWithoutDuplicates(
    currentMessages: Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }>,
    incomingMessages: Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }>
  ): Array<{ role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }> {
    const existingKeys = new Set(currentMessages.map((message) => this.buildMessageKey(message)));
    const nextMessages = [...currentMessages];

    for (const message of incomingMessages) {
      const key = this.buildMessageKey(message);
      if (existingKeys.has(key)) {
        continue;
      }

      existingKeys.add(key);
      nextMessages.push(message);
    }

    return nextMessages;
  }

  private buildMessageKey(message: { role: 'Paciente' | 'Estudiante' | 'Sistema'; time: string; content: string }): string {
    return `${message.role}|${message.time}|${message.content}`;
  }

  get patientNameDisplay(): string {
    return this.session.patientName?.trim() || 'Paciente simulado';
  }

  get patientAgeDisplay(): string {
    return this.session.age > 0 ? `${this.session.age} años` : 'Edad sin registro';
  }

  get patientSexDisplay(): string {
    return this.session.sex?.trim() || 'X';
  }

  get chiefComplaintDisplay(): string {
    return this.session.reason?.trim() || 'Sin motivo principal registrado.';
  }
}
