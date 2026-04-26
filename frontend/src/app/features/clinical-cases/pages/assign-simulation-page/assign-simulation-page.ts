import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
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

  constructor(
    private userContext: UserContext,
    private router: Router,
    private route: ActivatedRoute,
    private simulationAssignmentService: SimulationAssignmentService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    const caseId = this.route.snapshot.paramMap.get('id') ?? '1';
    this.simulationAssignmentService.getAssignmentContext(caseId).subscribe((context) => {
      this.clinicalCase = context.clinicalCase;
      this.students = context.students.map((student) => ({ ...student, selected: !!student.selected }));
      this.createSimulationPayload.clinicalCaseId = context.clinicalCase.id;
      this.isLoading = false;
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
    this.createSimulationPayload = {
      clinicalCaseId: this.clinicalCase.id,
      studentIds: this.students.filter((student) => student.selected).map((student) => student.id),
      mode: this.settings.mode === 'Con límite de tiempo' ? 'TIME_LIMITED' : 'UNLIMITED',
      durationMinutes: this.settings.mode === 'Con límite de tiempo' ? this.parseDuration(this.settings.duration) : undefined,
      availability:
        this.settings.availability === 'Programar fecha de inicio' ? 'SCHEDULED' : 'IMMEDIATE',
      availableAt: undefined
    };

    this.simulationAssignmentService.createSimulation(this.createSimulationPayload).subscribe();
    this.isCreateSuccess = true;

    setTimeout(() => {
      this.router.navigate(['/professor/dashboard']);
    }, 900);
  }

  private parseDuration(value: string): number | undefined {
    if (value === '15 minutos') return 15;
    if (value === '20 minutos') return 20;
    if (value === '30 minutos') return 30;
    return undefined;
  }
}
