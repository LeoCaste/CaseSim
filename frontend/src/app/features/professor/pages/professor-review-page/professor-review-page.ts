import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { SessionTranscript } from '../../components/session-transcript/session-transcript';
import { StudentNotebook } from '../../components/student-notebook/student-notebook';
import { EvaluationPanel } from '../../components/evaluation-panel/evaluation-panel';

@Component({
  selector: 'app-professor-review-page',
  imports: [CommonModule, SessionTranscript, StudentNotebook, EvaluationPanel],
  templateUrl: './professor-review-page.html',
  styleUrl: './professor-review-page.css',
})
export class ProfessorReviewPage {
  session = {
    student: 'Diego Muñoz',
    activity: 'Entrevista respiratoria',
    caseName: 'Catalina Paz Soto',
    status: 'Completada',
    duration: '12 min',
    turns: 18,
    submittedAt: '25/04/2026 · 10:42',
  };

  transcript = [
    {
      role: 'Paciente',
      time: '10:30',
      content: 'Vengo por tos seca y agotamiento desde hace cinco días.',
    },
    {
      role: 'Estudiante',
      time: '10:31',
      content: 'Gracias por comentarlo. ¿Desde cuándo nota que la tos empeora?',
    },
    {
      role: 'Paciente',
      time: '10:32',
      content: 'Empeora en la noche y me cuesta dormir bien.',
    },
    {
      role: 'Estudiante',
      time: '10:34',
      content: '¿Ha tenido fiebre, dolor torácico o dificultad para respirar?',
    },
    {
      role: 'Paciente',
      time: '10:35',
      content:
        'He tenido algo de fiebre baja y me canso más de lo habitual, pero no he sentido dolor fuerte en el pecho.',
    },
  ];

  notebook = {
    notes:
      'Paciente consulta por tos seca persistente y fatiga. Refiere empeoramiento nocturno y alteración del sueño. Pendiente explorar antecedentes respiratorios y contactos.',
    hypothesis:
      'Cuadro respiratorio subagudo. Considerar infección respiratoria atípica como hipótesis inicial.',
  };

  diagnosis = {
    finalDiagnosis: 'Neumonía atípica probable',
    reasoning:
      'Tos seca de varios días, fatiga, fiebre baja y evolución subaguda orientan a cuadro respiratorio compatible con neumonía atípica.',
  };

  constructor(private userContext: UserContext) {
    this.userContext.setRole('professor');
  }
}
