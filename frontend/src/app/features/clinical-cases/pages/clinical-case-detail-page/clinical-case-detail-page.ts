import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCase } from '../../../../core/models/clinical-case.model';
import { SimulationStudent } from '../../../../core/models/simulation.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';
import { SimulationAssignmentService } from '../../../../core/services/simulation-assignment.service';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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
  private readonly destroyRef = inject(DestroyRef);

  associatedStudents: Array<{ name: string; status: string; canReview: boolean }> = [];

  constructor(
    private userContext: UserContext,
    private route: ActivatedRoute,
    private clinicalCaseService: ClinicalCaseService,
    private simulationAssignmentService: SimulationAssignmentService,
    private cdr: ChangeDetectorRef
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

    this.clinicalCaseService
      .getById(caseId)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (clinicalCase) => {
          if (clinicalCase) {
            this.clinicalCase = clinicalCase;
          } else {
            this.error = 'No se encontró el caso clínico solicitado.';
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.error = 'No fue posible cargar el caso clínico.';
          this.cdr.detectChanges();
        }
      });

    this.simulationAssignmentService
      .getAssignmentContext(caseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (context) => {
          this.associatedStudents = context.students
            .filter((student) => student.selected ?? true)
            .map((student) => this.mapStudent(student));
          this.cdr.detectChanges();
        },
        error: () => {
          this.associatedStudents = [];
          this.cdr.detectChanges();
        }
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

  get statusLabel(): 'Listo' | 'Borrador' | 'Archivado' {
    return this.clinicalCase ? this.clinicalCaseService.getStatusLabel(this.clinicalCase.status) : 'Borrador';
  }

  get estimatedDurationLabel(): string {
    if (!this.clinicalCase?.estimatedTimeMinutes) return 'No definida';
    return `${this.clinicalCase.estimatedTimeMinutes} minutos`;
  }

  get canAssign(): boolean {
    return this.clinicalCase?.status === 'READY';
  }

  private mapStudent(student: SimulationStudent): { name: string; status: string; canReview: boolean } {
    return {
      name: student.name,
      status: student.status === 'COMPLETED' ? 'Completada' : student.status === 'IN_PROGRESS' ? 'En curso' : 'Pendiente',
      canReview: !!student.canReview
    };
  }
}
