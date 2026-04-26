import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserContext } from '../../../../core/services/user-context';
import { FinalDiagnosisModal } from '../../components/final-diagnosis-modal/final-diagnosis-modal';

interface InterviewMessage {
  role: 'Paciente' | 'Estudiante';
  time: string;
  content: string;
}

@Component({
  selector: 'app-interview-page',
  imports: [CommonModule, FormsModule, FinalDiagnosisModal],
  templateUrl: './interview-page.html',
  styleUrl: './interview-page.css'
})
export class InterviewPage {
  showFinalDiagnosisModal = false;
  clinicalIntervention = '';

  messages: InterviewMessage[] = [
    {
      role: 'Paciente',
      time: '6:00 AM',
      content: 'Vengo por tos seca y agotamiento desde hace cinco días.'
    },
    {
      role: 'Estudiante',
      time: '6:01 AM',
      content: 'Gracias por comentarlo. ¿Desde cuándo nota que la tos empeora?'
    },
    {
      role: 'Paciente',
      time: '6:02 AM',
      content: 'Empeora en la noche y me cuesta dormir bien.'
    }
  ];

  constructor(
    private userContext: UserContext,
    private router: Router
  ) {
    this.userContext.setRole('student');
  }

  sendIntervention(): void {
    const content = this.clinicalIntervention.trim();

    if (!content) {
      return;
    }

    this.messages.push({
      role: 'Estudiante',
      time: 'Ahora',
      content
    });

    this.clinicalIntervention = '';

    this.messages.push({
      role: 'Paciente',
      time: 'Ahora',
      content: 'Entiendo. Me pasa principalmente en la noche y me he sentido más cansada de lo normal.'
    });
  }

  openFinalDiagnosisModal(): void {
    this.showFinalDiagnosisModal = true;
  }

  closeFinalDiagnosisModal(): void {
    this.showFinalDiagnosisModal = false;
  }

  confirmFinalDiagnosis(): void {
    this.showFinalDiagnosisModal = false;
    this.router.navigate(['/session-completed']);
  }
}
