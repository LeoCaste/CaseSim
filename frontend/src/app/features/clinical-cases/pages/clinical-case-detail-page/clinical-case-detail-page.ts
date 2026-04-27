import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCase } from '../../../../core/models/clinical-case.model';
import { SimulationStudent } from '../../../../core/models/simulation.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';
import { SimulationAssignmentService } from '../../../../core/services/simulation-assignment.service';

@Component({
  selector: 'app-clinical-case-detail-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-detail-page.html',
  styleUrl: './clinical-case-detail-page.css',
})
export class ClinicalCaseDetailPage implements OnInit {
  loading = false;
  error = '';
  clinicalCase?: ClinicalCase;

  associatedStudents: Array<{ name: string; status: string; canReview: boolean }> = [];

  constructor(
    private userContext: UserContext,
    private route: ActivatedRoute,
    private clinicalCaseService: ClinicalCaseService,
    private simulationAssignmentService: SimulationAssignmentService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    const caseId = this.route.snapshot.paramMap.get('id');
    if (!caseId) {
      this.error = 'No se encontró el caso clínico solicitado.';
      return;
    }

    this.loading = true;
    this.error = '';

    this.clinicalCaseService.getById(caseId).subscribe((clinicalCase) => {
      if (clinicalCase) {
        this.clinicalCase = clinicalCase;
      } else {
        this.error = 'No se encontró el caso clínico solicitado.';
      }
      this.loading = false;
    });

    this.simulationAssignmentService.getAssignmentContext(caseId).subscribe((context) => {
      this.associatedStudents = context.students
        .filter((student) => student.selected ?? true)
        .map((student) => this.mapStudent(student));
    });
  }

  get pageTitle(): string {
    if (!this.clinicalCase) {
      return 'Detalle del caso';
    }
    return `Caso ${this.clinicalCase.patientName}`;
  }

  getFactVisibilityLabel(visibility: 'INITIAL' | 'ON_QUESTION'): 'Inicial' | 'Bajo pregunta' {
    return this.clinicalCaseService.getFactVisibilityLabel(visibility);
  }

  get personalityTone(): string {
    return this.clinicalCase?.personality.tone || 'Natural y colaborador';
  }

  get personalityDetailLevel(): string {
    return this.clinicalCase?.personality.detailLevel || 'Responder solo lo preguntado';
  }

  get personalityBehaviorNotes(): string {
    return this.clinicalCase?.personality.behaviorNotes || this.clinicalCase?.behaviorGuidelines || '';
  }

  private mapStudent(student: SimulationStudent): { name: string; status: string; canReview: boolean } {
    return {
      name: student.name,
      status: student.status === 'COMPLETED' ? 'Completada' : student.status === 'IN_PROGRESS' ? 'En curso' : 'Pendiente',
      canReview: !!student.canReview
    };
  }
}
