import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';

export interface DiagnosisReview {
  finalDiagnosis: string;
  reasoning: string;
}

@Component({
  selector: 'app-evaluation-panel',
  imports: [],
  templateUrl: './evaluation-panel.html',
  styleUrl: './evaluation-panel.css'
})
export class EvaluationPanel {
  @Input({ required: true }) diagnosis!: DiagnosisReview;

  constructor(private router: Router) {}

  finishReview(): void {
    this.router.navigate(['/professor/dashboard']);
  }
}
