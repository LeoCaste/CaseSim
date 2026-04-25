import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

import { UserContext } from '../../../../core/services/user-context';
import { FinalDiagnosisModal } from '../../components/final-diagnosis-modal/final-diagnosis-modal';

@Component({
  selector: 'app-interview-page',
  imports: [CommonModule, FinalDiagnosisModal],
  templateUrl: './interview-page.html',
  styleUrl: './interview-page.css'
})
export class InterviewPage {
  showFinalDiagnosisModal = false;

  constructor(
    private userContext: UserContext,
    private router: Router
  ) {
    this.userContext.setRole('student');
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
