import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-final-diagnosis-modal',
  imports: [CommonModule, FormsModule],
  templateUrl: './final-diagnosis-modal.html',
  styleUrl: './final-diagnosis-modal.css'
})
export class FinalDiagnosisModal {
  @Input() diagnosis = '';
  @Input() reasoning = '';

  validationMessage = '';

  @Output() cancel = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<{ diagnosis: string; reasoning: string }>();

  onConfirm(): void {
    const trimmedDiagnosis = this.diagnosis.trim();

    if (!trimmedDiagnosis) {
      this.validationMessage = 'Escribe un diagnóstico antes de enviarlo.';
      return;
    }

    this.confirm.emit({
      diagnosis: trimmedDiagnosis,
      reasoning: this.reasoning
    });
  }

  onDiagnosisChange(value: string): void {
    this.diagnosis = value;
    this.validationMessage = '';
  }

  get canConfirm(): boolean {
    return this.diagnosis.trim().length > 0;
  }
}
