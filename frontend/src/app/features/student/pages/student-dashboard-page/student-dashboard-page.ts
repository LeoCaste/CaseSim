import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { ActivityCard } from '../../components/activity-card/activity-card';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-student-dashboard-page',
  imports: [CommonModule, ActivityCard, RouterLink],
  templateUrl: './student-dashboard-page.html',
  styleUrl: './student-dashboard-page.css'
})
export class StudentDashboardPage {
  activities = [
    {
      title: 'Caso de prueba respiratorio',
      course: 'Caso de prueba',
      professor: 'Equipo docente',
      patient: 'Catalina Paz Soto',
      status: 'Disponible',
      statusType: 'success',
      duration: 'Sin límite de tiempo',
      description: 'Realiza una anamnesis clínica y concluye la atención cuando tengas una hipótesis diagnóstica.',
      actionLabel: 'Iniciar entrevista',
      route: '/student/waiting-room'
    },
    {
      title: 'Caso de prueba cardiovascular',
      course: 'Caso de prueba',
      professor: 'Equipo docente',
      patient: 'Roberto Alarcón',
      status: 'Pendiente',
      statusType: 'neutral',
      duration: '20 minutos',
      description: 'Actividad de prueba pendiente de habilitación.',
      actionLabel: 'No disponible',
      route: null
    }
  ];

  history = [
    {
      title: 'Caso de prueba respiratorio',
      patient: 'Catalina Paz Soto',
      status: 'Registrada',
      date: '25/04/2026',
      route: '/student/session-detail'
    }
  ];

  constructor(private userContext: UserContext) {
    this.userContext.setRole('student');
  }
}
