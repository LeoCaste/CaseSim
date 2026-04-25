import { Component } from '@angular/core';
import { UserContext } from '../../../../core/services/user-context';
import { ActivityCard } from '../../components/activity-card/activity-card';

@Component({
  selector: 'app-student-dashboard-page',
  imports: [ActivityCard],
  templateUrl: './student-dashboard-page.html',
  styleUrl: './student-dashboard-page.css'
})
export class StudentDashboardPage {
  activities = [
    {
      title: 'Entrevista respiratoria',
      course: 'Semiología · Tercer año',
      professor: 'Dr. Fernández',
      patient: 'Catalina Paz Soto',
      status: 'Disponible',
      statusType: 'success',
      duration: 'Sin límite de tiempo',
      description: 'Realiza una anamnesis clínica y concluye la atención cuando tengas una hipótesis diagnóstica.',
      actionLabel: 'Iniciar entrevista',
      route: '/interview'
    },
    {
      title: 'Caso cardiovascular',
      course: 'Medicina Interna',
      professor: 'Dra. Morales',
      patient: 'Roberto Alarcón',
      status: 'Pendiente',
      statusType: 'neutral',
      duration: '20 minutos',
      description: 'Actividad asignada. Estará disponible cuando el profesor habilite la sesión.',
      actionLabel: 'No disponible',
      route: null
    },
    {
      title: 'Entrevista respiratoria completada',
      course: 'Semiología · Tercer año',
      professor: 'Dr. Fernández',
      patient: 'Catalina Paz Soto',
      status: 'Completada',
      statusType: 'warning',
      duration: 'Registrada',
      description: 'Tu entrevista fue registrada y queda disponible para revisión del profesor.',
      actionLabel: 'Revisar estado',
      route: '/session-completed'
    }
  ];

  constructor(private userContext: UserContext) {
    this.userContext.setRole('student');
  }
}
