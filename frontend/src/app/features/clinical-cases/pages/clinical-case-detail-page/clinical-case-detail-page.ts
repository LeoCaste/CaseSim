import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCase } from '../../../../core/models/clinical-case.model';
import { SimulationStudent } from '../../../../core/models/simulation.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';
import { SimulationAssignmentService } from '../../../../core/services/simulation-assignment.service';

interface ClinicalCaseDetailView {
  id: string;
  patientName: string;
  title: string;
  age: number;
  sex: string;
  context: string;
  reason: string;
  initialMessage: string;
  diagnosis: string;
  fallback: string;
  behavior: string;
  facts: Array<{
    category: string;
    title: string;
    trigger: string;
    visibility: 'Inicial' | 'Bajo pregunta';
  }>;
}

@Component({
  selector: 'app-clinical-case-detail-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-detail-page.html',
  styleUrl: './clinical-case-detail-page.css',
})
export class ClinicalCaseDetailPage implements OnInit {
  clinicalCase: ClinicalCaseDetailView = {
    id: '1',
    patientName: '',
    title: '',
    age: 0,
    sex: 'F',
    context: '',
    reason: '',
    initialMessage: '',
    diagnosis: '',
    fallback: '',
    behavior: '',
    facts: []
  };

  associatedStudents: Array<{ name: string; status: string; canReview: boolean }> = [];
  isLoading = false;
  loadError = '';

  constructor(
    private userContext: UserContext,
    private route: ActivatedRoute,
    private clinicalCaseService: ClinicalCaseService,
    private simulationAssignmentService: SimulationAssignmentService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    const caseId = this.route.snapshot.paramMap.get('id') ?? '1';

    this.clinicalCaseService.getClinicalCaseById(caseId).subscribe((clinicalCase) => {
      if (clinicalCase) {
        this.clinicalCase = this.mapClinicalCase(clinicalCase);
      }
      this.isLoading = false;
    });

    this.simulationAssignmentService.getAssignmentContext(caseId).subscribe((context) => {
      this.associatedStudents = context.students
        .filter((student) => student.selected ?? true)
        .map((student) => this.mapStudent(student));
    });
  }

  private mapClinicalCase(clinicalCase: ClinicalCase): ClinicalCaseDetailView {
    return {
      id: clinicalCase.id,
      patientName: clinicalCase.patientName,
      title: `Caso ${clinicalCase.patientName}`,
      age: clinicalCase.age,
      sex: clinicalCase.sex,
      context: clinicalCase.context,
      reason: clinicalCase.reason,
      initialMessage: clinicalCase.initialMessage,
      diagnosis: clinicalCase.expectedDiagnosis ?? '',
      fallback: clinicalCase.fallbackResponse ?? '',
      behavior: clinicalCase.behaviorGuidelines ?? '',
      facts: clinicalCase.facts.map((fact) => ({
        category: fact.category,
        title: fact.title,
        trigger: fact.trigger,
        visibility: fact.visibility === 'INITIAL' ? 'Inicial' : 'Bajo pregunta'
      }))
    };
  }

  private mapStudent(student: SimulationStudent): { name: string; status: string; canReview: boolean } {
    return {
      name: student.name,
      status: student.status === 'COMPLETED' ? 'Completada' : student.status === 'IN_PROGRESS' ? 'En curso' : 'Pendiente',
      canReview: !!student.canReview
    };
  }
}
