import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-final-diagnosis-modal',
  imports: [FormsModule],
  templateUrl: './final-diagnosis-modal.html',
  styleUrl: './final-diagnosis-modal.css'
})
export class FinalDiagnosisModal {
  @Input() diagnosis = '';
  @Input() reasoning = '';

  @Output() cancel = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<{ diagnosis: string; reasoning: string }>();

  onConfirm(): void {
    this.confirm.emit({
      diagnosis: this.diagnosis,
      reasoning: this.reasoning
    });
  }
}
