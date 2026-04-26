import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

export interface ClinicalCaseCardData {
  patientName: string;
  title: string;
  age: number;
  sex: string;
  reason: string;
  status: string;
  difficulty: string;
  estimatedTime: string;
  facts: number;
}

@Component({
  selector: 'app-clinical-case-card',
  imports: [CommonModule],
  templateUrl: './clinical-case-card.html',
  styleUrl: './clinical-case-card.css',
})
export class ClinicalCaseCard {
  @Input({ required: true }) clinicalCase!: ClinicalCaseCardData;
}
