import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-final-diagnosis-modal',
  imports: [],
  templateUrl: './final-diagnosis-modal.html',
  styleUrl: './final-diagnosis-modal.css'
})
export class FinalDiagnosisModal {
  @Output() cancel = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();
}
