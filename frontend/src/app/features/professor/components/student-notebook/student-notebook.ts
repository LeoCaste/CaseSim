import { Component, Input } from '@angular/core';

export interface StudentNotebookData {
  notes: string;
  hypothesis: string;
}

@Component({
  selector: 'app-student-notebook',
  imports: [],
  templateUrl: './student-notebook.html',
  styleUrl: './student-notebook.css',
})
export class StudentNotebook {
  @Input({ required: true }) notebook!: StudentNotebookData;
}
