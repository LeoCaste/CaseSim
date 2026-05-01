import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-error-modal',
  imports: [CommonModule],
  templateUrl: './error-modal.html',
  styleUrl: './error-modal.css'
})
export class ErrorModal {
  @Input() title = 'No se pudo completar la acción';
  @Input() detail = '';
  @Input() detailList: string[] = [];
  @Input() suggestion = '';
  @Input() confirmLabel = 'Entendido';
  @Input() showRetry = false;
  @Input() retryLabel = 'Reintentar';

  @Output() close = new EventEmitter<void>();
  @Output() retry = new EventEmitter<void>();
}
