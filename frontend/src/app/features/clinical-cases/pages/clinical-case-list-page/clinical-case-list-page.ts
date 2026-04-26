import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCaseCard } from '../../components/clinical-case-card/clinical-case-card';

@Component({
  selector: 'app-clinical-case-list-page',
  imports: [CommonModule, ClinicalCaseCard],
  templateUrl: './clinical-case-list-page.html',
  styleUrl: './clinical-case-list-page.css',
})
export class ClinicalCaseListPage {
  cases = [
    {
      patientName: 'Catalina Paz Soto',
      title: 'Entrevista respiratoria',
      age: 22,
      sex: 'F',
      reason: 'Tos seca persistente y fatiga de 5 días',
      status: 'Listo',
      difficulty: 'Formativo',
      estimatedTime: 'Sin límite de tiempo',
      facts: 8,
    },
    {
      patientName: 'Roberto Alarcón',
      title: 'Caso cardiovascular',
      age: 58,
      sex: 'M',
      reason: 'Dolor torácico intermitente',
      status: 'Borrador',
      difficulty: 'Formativo',
      estimatedTime: '20 minutos',
      facts: 5,
    },
  ];

  constructor(private userContext: UserContext) {
    this.userContext.setRole('professor');
  }
}
