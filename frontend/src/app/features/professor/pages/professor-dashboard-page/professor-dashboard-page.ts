import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';

import { ProfessorCourseCard } from '../../components/professor-course-card/professor-course-card';
import { ProfessorActivitySummary } from '../../components/professor-activity-summary/professor-activity-summary';
import { RecentSessionTable } from '../../components/recent-session-table/recent-session-table';

@Component({
  selector: 'app-professor-dashboard-page',
  imports: [CommonModule, ProfessorCourseCard, ProfessorActivitySummary, RecentSessionTable],
  templateUrl: './professor-dashboard-page.html',
  styleUrl: './professor-dashboard-page.css',
})
export class ProfessorDashboardPage {
  summary = [
    { label: 'Simulación activa', value: '1' },
    { label: 'Estudiantes asignados', value: '32' },
    { label: 'Sesiones en curso', value: '8' },
    { label: 'Sesiones completadas', value: '18' },
  ];

  simulations = [
    {
      name: 'Entrevista respiratoria',
      caseName: 'Catalina Paz Soto',
      course: 'Semiología · Tercer año',
      students: 32,
      completedSessions: 18,
    },
  ];

  activities = [
    {
      title: 'Entrevista respiratoria',
      course: 'Semiología · Tercer año',
      caseName: 'Catalina Paz Soto',
      status: 'Activa',
      completed: 18,
      total: 32,
    },
    {
      title: 'Caso cardiovascular',
      course: 'Medicina Interna',
      caseName: 'Roberto Alarcón',
      status: 'Borrador',
      completed: 0,
      total: 28,
    },
  ];

  sessions = [
    {
      student: 'Diego Muñoz',
      activity: 'Entrevista respiratoria',
      status: 'Completada',
      turns: 18,
      duration: '12 min',
    },
    {
      student: 'Valentina Ríos',
      activity: 'Entrevista respiratoria',
      status: 'En curso',
      turns: 9,
      duration: '7 min',
    },
  ];

  constructor(private userContext: UserContext) {
    this.userContext.setRole('professor');
  }
}
