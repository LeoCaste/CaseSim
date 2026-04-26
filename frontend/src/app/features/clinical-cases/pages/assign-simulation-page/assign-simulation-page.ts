import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-assign-simulation-page',
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './assign-simulation-page.html',
  styleUrl: './assign-simulation-page.css'
})
export class AssignSimulationPage {
  clinicalCase = {
    id: 1,
    title: 'Caso Catalina Paz Soto',
    patientName: 'Catalina Paz Soto',
    reason: 'tos seca y fatiga'
  };

  showCreateConfirmation = false;
  isCreateSuccess = false;

  students = [
    { name: 'Diego Muñoz', selected: true },
    { name: 'Valentina Ríos', selected: true },
    { name: 'Matías Soto', selected: true },
    { name: 'Isidora Vega', selected: false }
  ];

  settings = {
    mode: 'Sin límite de tiempo',
    duration: 'No aplica',
    availability: 'Disponible inmediatamente'
  };

  constructor(
    private userContext: UserContext,
    private router: Router
  ) {
    this.userContext.setRole('professor');
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
    this.isCreateSuccess = true;

    setTimeout(() => {
      this.router.navigate(['/professor/dashboard']);
    }, 900);
  }
}
