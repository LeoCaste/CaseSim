import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, take } from 'rxjs';
import { ClinicalCaseSummary } from '../../../../core/models/clinical-case.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';

@Component({
  selector: 'app-clinical-case-card',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-card.html',
  styleUrl: './clinical-case-card.css',
})
export class ClinicalCaseCard {
  @Input({ required: true }) clinicalCase!: ClinicalCaseSummary;
  @Output() deleted = new EventEmitter<string>();

  deleting = false;
  deleteError = '';
  deleteSuccess = '';

  constructor(private clinicalCaseService: ClinicalCaseService) {}

  get estimatedTimeLabel(): string {
    return this.clinicalCase.estimatedTimeMinutes === undefined
      ? 'Sin límite de tiempo'
      : `${this.clinicalCase.estimatedTimeMinutes} minutos`;
  }

  get statusLabel(): 'Listo' | 'Borrador' | 'Archivado' {
    return this.clinicalCaseService.getStatusLabel(this.clinicalCase.status);
  }

  get canAssign(): boolean {
    return this.clinicalCase.status === 'READY';
  }

  onDeleteCase(): void {
    this.deleteError = '';
    this.deleteSuccess = '';

    const confirmed = window.confirm(
      `¿Eliminar el caso clínico "${this.clinicalCase.title}"? Esta acción no se puede deshacer.`
    );

    if (!confirmed) {
      return;
    }

    this.deleting = true;

    this.clinicalCaseService
      .delete(this.clinicalCase.id)
      .pipe(
        take(1),
        finalize(() => {
          this.deleting = false;
        })
      )
      .subscribe({
        next: () => {
          this.deleteSuccess = 'Caso clínico eliminado correctamente.';
          this.deleted.emit(this.clinicalCase.id);
        },
        error: (error: unknown) => {
          this.deleteError = this.mapDeleteError(error);
        }
      });
  }

  private mapDeleteError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'No fue posible eliminar el caso clínico. Intenta nuevamente.';
    }

    if (error.status === 401) {
      return 'Tu sesión expiró. Inicia sesión nuevamente para eliminar el caso.';
    }

    if (error.status === 403) {
      return 'No tienes permisos para eliminar este caso clínico.';
    }

    if (error.status === 404) {
      return 'El caso clínico ya no existe o fue eliminado previamente.';
    }

    return 'No fue posible eliminar el caso clínico. Intenta nuevamente.';
  }
}
