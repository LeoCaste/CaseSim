import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-clinical-case-detail-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-detail-page.html',
  styleUrl: './clinical-case-detail-page.css',
})
export class ClinicalCaseDetailPage {
  clinicalCase = {
    id: 1,
    patientName: 'Catalina Paz Soto',
    title: 'Entrevista respiratoria',
    age: 22,
    sex: 'F',
    context: 'Consulta ambulatoria',
    reason: 'Tos seca persistente y fatiga de 5 días.',
    initialMessage: 'Vengo porque tengo una tos seca que no se me pasa y me siento muy agotada.',
    diagnosis: 'Neumonía atípica probable',
    fallback: 'No estoy segura, no sabría decir.',
    behavior:
      'Paciente responde de forma natural, breve y coherente. No entrega información que no se le haya preguntado. Puede mostrar leve preocupación por su estado.',
    facts: [
      {
        category: 'Síntoma',
        title: 'Tos seca persistente',
        trigger: 'tos, respiratorio',
        visibility: 'Inicial',
      },
      {
        category: 'Evolución',
        title: 'Fatiga de 5 días',
        trigger: 'duración, evolución',
        visibility: 'Bajo pregunta',
      },
      {
        category: 'Antecedente epidemiológico',
        title: 'Contacto con persona enferma',
        trigger: 'contactos, contagio',
        visibility: 'Bajo pregunta',
      },
    ],
  };

  constructor(private userContext: UserContext) {
    this.userContext.setRole('professor');
  }
}
