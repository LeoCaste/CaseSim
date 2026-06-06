import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserContext } from '../../../../core/services/user-context';
import {
  CreateSimulationPayload,
  SimulationAssignmentService
} from '../../../../core/services/simulation-assignment.service';
import { SimulationStudent } from '../../../../core/models/simulation.model';
import { ClinicalCaseStatus } from '../../../../core/models/clinical-case.model';

@Component({
  selector: 'app-assign-simulation-page',
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './assign-simulation-page.html',
  styleUrl: './assign-simulation-page.css'
})
export class AssignSimulationPage implements OnInit {
  clinicalCase: {
    id: string;
    title: string;
    patientName: string;
    reason: string;
    status: ClinicalCaseStatus;
    estimatedTimeMinutes?: number;
  } = {
    id: '1',
    title: 'Caso Catalina Paz Soto',
    patientName: 'Catalina Paz Soto',
    reason: 'tos seca y fatiga',
    status: 'READY'
  };

  showCreateConfirmation = false;
  isCreateSuccess = false;

  students: Array<SimulationStudent & { selected: boolean }> = [];

  createSimulationPayload: CreateSimulationPayload = {
    clinicalCaseId: '1',
    studentIds: []
  };
  isLoading = false;
  loadError = '';
  isSubmitting = false;
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private userContext: UserContext,
    private router: Router,
    private route: ActivatedRoute,
    private simulationAssignmentService: SimulationAssignmentService,
    private cdr: ChangeDetectorRef
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';
    const caseId = this.route.snapshot.paramMap.get('id') ?? '1';
    this.simulationAssignmentService
      .getAssignmentContext(caseId)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (context) => {
          this.clinicalCase = context.clinicalCase;
          this.students = context.students.map((student) => ({ ...student, selected: !!student.selected }));
          this.createSimulationPayload.clinicalCaseId = context.clinicalCase.id;
          this.cdr.detectChanges();
        },
        error: (error) => {
          this.loadError = error?.message ?? 'No fue posible cargar la lista de estudiantes.';
          this.students = [];
          this.cdr.detectChanges();
        }
      });
  }

  createSimulation(): void {
    this.loadError = '';
    if (!this.canAssignCase) {
      this.loadError = 'Este caso aún no está listo para ser asignado.';
      this.showCreateConfirmation = false;
      this.isCreateSuccess = false;
      this.cdr.detectChanges();
      return;
    }

    this.showCreateConfirmation = true;
    this.isCreateSuccess = false;
  }

  cancelCreateSimulation(): void {
    this.showCreateConfirmation = false;
    this.isCreateSuccess = false;
  }

  confirmCreateSimulation(): void {
    this.loadError = '';
    if (!this.canAssignCase) {
      this.loadError = 'Este caso aún no está listo para ser asignado.';
      this.showCreateConfirmation = false;
      this.isCreateSuccess = false;
      this.cdr.detectChanges();
      return;
    }

    this.createSimulationPayload = {
      clinicalCaseId: this.clinicalCase.id,
      studentIds: this.students.filter((student) => student.selected).map((student) => student.id)
    };

    if (this.createSimulationPayload.studentIds.length === 0) {
      this.loadError = 'Debes seleccionar al menos un estudiante para crear la simulación.';
      this.isCreateSuccess = false;
      this.cdr.detectChanges();
      return;
    }

    this.isSubmitting = true;
    this.simulationAssignmentService
      .createSimulation(this.createSimulationPayload)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSubmitting = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.isCreateSuccess = true;
          this.cdr.detectChanges();
          this.router.navigate(['/professor/dashboard']);
        },
        error: (error) => {
          this.loadError = error?.message ?? 'No fue posible crear la simulación.';
          this.isCreateSuccess = false;
          this.cdr.detectChanges();
        }
      });
  }

  get canAssignCase(): boolean {
    return this.clinicalCase.status === 'READY';
  }

  get estimatedDurationLabel(): string {
    if (!this.clinicalCase.estimatedTimeMinutes) return 'No definida';
    return `${this.clinicalCase.estimatedTimeMinutes} minutos`;
  }

  get statusLabel(): 'Listo' | 'Borrador' | 'Archivado' {
    if (this.clinicalCase.status === 'READY') return 'Listo';
    if (this.clinicalCase.status === 'DRAFT') return 'Borrador';
    return 'Archivado';
  }
}
