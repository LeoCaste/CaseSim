import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';

interface ClinicalFact {
  category: string;
  title: string;
  trigger: string;
  visibility: 'Inicial' | 'Bajo pregunta';
}

@Component({
  selector: 'app-clinical-case-form-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-form-page.html',
  styleUrl: './clinical-case-form-page.css'
})
export class ClinicalCaseFormPage {
  isEditMode = false;
  patientName = 'Catalina Paz Soto';
  showSaveModal = false;
  isSaveSuccess = false;

  clinicalFacts: ClinicalFact[] = [
    {
      category: 'Síntoma',
      title: 'Tos seca persistente',
      trigger: 'tos, respiratorio',
      visibility: 'Inicial'
    },
    {
      category: 'Evolución',
      title: 'Fatiga de 5 días',
      trigger: 'duración, evolución',
      visibility: 'Bajo pregunta'
    },
    {
      category: 'Antecedente epidemiológico',
      title: 'Contacto con persona enferma',
      trigger: 'contactos, contagio',
      visibility: 'Bajo pregunta'
    }
  ];

  constructor(
    private userContext: UserContext,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.userContext.setRole('professor');
    this.isEditMode = this.route.snapshot.paramMap.has('id');
  }

  get pageTitle(): string {
    return this.isEditMode
      ? `Editar caso ${this.patientName}`
      : `Caso ${this.patientName}`;
  }

  openSaveConfirmation(): void {
    this.showSaveModal = true;
    this.isSaveSuccess = false;
  }

  cancelSaveConfirmation(): void {
    this.showSaveModal = false;
    this.isSaveSuccess = false;
  }

  confirmSaveCase(): void {
    this.isSaveSuccess = true;

    setTimeout(() => {
      this.router.navigate(['/clinical-cases']);
    }, 900);
  }

  get saveLabel(): string {
    return this.isEditMode ? 'Guardar cambios' : 'Guardar caso';
  }
}
