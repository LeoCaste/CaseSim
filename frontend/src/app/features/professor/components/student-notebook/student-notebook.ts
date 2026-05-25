import { Component, Input } from '@angular/core';
import { ProfessorSessionReview } from '../../../../core/models/professor-dashboard.model';

export type StudentNotebookData = ProfessorSessionReview['notebook'];

@Component({
  selector: 'app-student-notebook',
  imports: [],
  templateUrl: './student-notebook.html',
  styleUrl: './student-notebook.css',
})
export class StudentNotebook {
  @Input({ required: true }) notebook!: StudentNotebookData;
}
