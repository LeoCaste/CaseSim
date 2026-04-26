import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-student-session-detail-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './student-session-detail-page.html',
  styleUrl: './student-session-detail-page.css'
})
export class StudentSessionDetailPage {
  messages = [
    { role: 'Paciente', time: '10:30', content: 'Vengo por tos seca y agotamiento desde hace cinco días.' },
    { role: 'Estudiante', time: '10:31', content: '¿Desde cuándo comenzó con la tos?' },
    { role: 'Paciente', time: '10:32', content: 'Hace unos cinco días. Empeora por la noche.' }
  ];

  constructor(private userContext: UserContext) {
    this.userContext.setRole('student');
  }
}
