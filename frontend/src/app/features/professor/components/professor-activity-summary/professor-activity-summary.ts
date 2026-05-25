import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProfessorActivityOverview as ProfessorActivity } from '../../../../core/models/professor-dashboard.model';

@Component({
  selector: 'app-professor-activity-summary',
  imports: [CommonModule],
  templateUrl: './professor-activity-summary.html',
  styleUrl: './professor-activity-summary.css',
})
export class ProfessorActivitySummary {
  @Input({ required: true }) activity!: ProfessorActivity;

  get progress(): number {
    if (this.activity.total === 0) return 0;
    return Math.round((this.activity.completed / this.activity.total) * 100);
  }
}
