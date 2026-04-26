import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
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

  constructor(private clinicalCaseService: ClinicalCaseService) {}

  get estimatedTimeLabel(): string {
    return this.clinicalCase.estimatedTimeMinutes === undefined
      ? 'Sin límite de tiempo'
      : `${this.clinicalCase.estimatedTimeMinutes} minutos`;
  }

  get statusLabel(): 'Listo' | 'Borrador' | 'Archivado' {
    return this.clinicalCaseService.getStatusLabel(this.clinicalCase.status);
  }
}
