import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DiagnosisReview } from '../../../../core/models/student-session.model';

@Component({
  selector: 'app-evaluation-panel',
  imports: [CommonModule, FormsModule],
  templateUrl: './evaluation-panel.html',
  styleUrl: './evaluation-panel.css'
})
export class EvaluationPanel {
  @Input({ required: true }) diagnosis!: DiagnosisReview;

  showFeedbackEditor = false;
  isEditingFeedback = false;
  feedbackDraft = '';
  feedbackText =
    'Buen análisis inicial. Profundiza en antecedentes respiratorios para reforzar el diagnóstico diferencial.';
  feedbackNotice = '';
  showDeleteConfirmation = false;

  showFinishModal = false;
  isFinishSuccess = false;

  constructor(private router: Router) {}

  openFeedbackEditor(): void {
    this.showFeedbackEditor = true;
    this.isEditingFeedback = false;
    this.feedbackDraft = this.feedbackText;
    this.feedbackNotice = '';
    this.showDeleteConfirmation = false;
  }

  editFeedback(): void {
    this.showFeedbackEditor = true;
    this.isEditingFeedback = true;
    this.feedbackDraft = this.feedbackText;
    this.feedbackNotice = '';
    this.showDeleteConfirmation = false;
  }

  cancelFeedbackEditor(): void {
    this.showFeedbackEditor = false;
    this.feedbackDraft = this.feedbackText;
  }

  saveFeedback(): void {
    const text = this.feedbackDraft.trim();

    if (!text) {
      return;
    }

    this.feedbackText = text;
    this.showFeedbackEditor = false;
    this.feedbackNotice = this.isEditingFeedback
      ? 'Feedback actualizado.'
      : 'Feedback guardado.';
    this.isEditingFeedback = false;
    this.showDeleteConfirmation = false;
  }

  requestDeleteFeedback(): void {
    this.showDeleteConfirmation = true;
    this.feedbackNotice = '';
  }

  cancelDeleteFeedback(): void {
    this.showDeleteConfirmation = false;
  }

  confirmDeleteFeedback(): void {
    this.feedbackText = '';
    this.feedbackDraft = '';
    this.feedbackNotice = 'Feedback eliminado.';
    this.showDeleteConfirmation = false;
    this.showFeedbackEditor = false;
    this.isEditingFeedback = false;
  }

  openFinishReviewModal(): void {
    this.showFinishModal = true;
    this.isFinishSuccess = false;
  }

  cancelFinishReviewModal(): void {
    this.showFinishModal = false;
    this.isFinishSuccess = false;
  }

  confirmFinishReview(): void {
    this.isFinishSuccess = true;
  }

  finishReview(): void {
    this.showFinishModal = false;
    this.isFinishSuccess = false;
    this.router.navigate(['/professor/dashboard']);
  }
}
