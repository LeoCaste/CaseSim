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

@Component({
  selector: 'app-assign-simulation-page',
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './assign-simulation-page.html',
  styleUrl: './assign-simulation-page.css'
})
export class AssignSimulationPage implements OnInit {
  clinicalCase = {
    id: '1',
    title: 'Caso Catalina Paz Soto',
    patientName: 'Catalina Paz Soto',
    reason: 'tos seca y fatiga'
  };

  showCreateConfirmation = false;
  isCreateSuccess = false;

  students: Array<SimulationStudent & { selected: boolean }> = [];

  settings = {
    mode: 'Sin límite de tiempo',
    duration: 'No aplica',
    availability: 'Disponible inmediatamente'
  };
  createSimulationPayload: CreateSimulationPayload = {
    clinicalCaseId: '1',
    studentIds: [],
    mode: 'UNLIMITED',
    availability: 'IMMEDIATE'
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

  onModeChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.settings.mode = value;

    if (value === 'Sin límite de tiempo') {
      this.settings.duration = 'No aplica';
    }
  }

  createSimulation(): void {
    this.showCreateConfirmation = true;
    this.isCreateSuccess = false;
  }

  cancelCreateSimulation(): void {
    this.showCreateConfirmation = false;
    this.isCreateSuccess = false;
  }

  confirmCreateSimulation(): void {
    this.loadError = '';
    this.createSimulationPayload = {
      clinicalCaseId: this.clinicalCase.id,
      studentIds: this.students.filter((student) => student.selected).map((student) => student.id),
      mode: this.settings.mode === 'Con límite de tiempo' ? 'TIME_LIMITED' : 'UNLIMITED',
      durationMinutes: this.settings.mode === 'Con límite de tiempo' ? this.parseDuration(this.settings.duration) : undefined,
      availability:
        this.settings.availability === 'Programar fecha de inicio' ? 'SCHEDULED' : 'IMMEDIATE',
      availableAt: undefined
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

  private parseDuration(value: string): number | undefined {
    if (value === '15 minutos') return 15;
    if (value === '20 minutos') return 20;
    if (value === '30 minutos') return 30;
    return undefined;
  }
}
