import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClinicalCaseCardView as ClinicalCaseCardData } from '../../../../core/services/clinical-case.service';

@Component({
  selector: 'app-clinical-case-card',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-card.html',
  styleUrl: './clinical-case-card.css',
})
export class ClinicalCaseCard {
  @Input({ required: true }) clinicalCase!: ClinicalCaseCardData;
}
