import { Component, Input } from '@angular/core';
import { ProfessorSimulationOverview as ProfessorSimulation } from '../../../../core/models/professor-dashboard.model';

@Component({
  selector: 'app-professor-course-card',
  imports: [],
  templateUrl: './professor-course-card.html',
  styleUrl: './professor-course-card.css',
})
export class ProfessorCourseCard {
  @Input({ required: true }) simulation!: ProfessorSimulation;
}
