import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
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
    private route: ActivatedRoute
  ) {
    this.userContext.setRole('professor');
    this.isEditMode = this.route.snapshot.paramMap.has('id');
  }

  get pageTitle(): string {
    return this.isEditMode ? 'Editar caso simulado' : 'Crear caso simulado';
  }

  get saveLabel(): string {
    return this.isEditMode ? 'Guardar cambios' : 'Guardar caso';
  }
}
