import { Component, Input } from '@angular/core';

export interface DiagnosisReview {
  finalDiagnosis: string;
  reasoning: string;
}

@Component({
  selector: 'app-evaluation-panel',
  imports: [],
  templateUrl: './evaluation-panel.html',
  styleUrl: './evaluation-panel.css',
})
export class EvaluationPanel {
  @Input({ required: true }) diagnosis!: DiagnosisReview;
}
