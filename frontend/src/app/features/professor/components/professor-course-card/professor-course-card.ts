import { Component, Input } from '@angular/core';

export interface ProfessorSimulation {
  name: string;
  caseName: string;
  course: string;
  students: number;
  completedSessions: number;
}

@Component({
  selector: 'app-professor-course-card',
  imports: [],
  templateUrl: './professor-course-card.html',
  styleUrl: './professor-course-card.css',
})
export class ProfessorCourseCard {
  @Input({ required: true }) simulation!: ProfessorSimulation;
}
