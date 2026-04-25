import { Component } from '@angular/core';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-interview-page',
  imports: [],
  templateUrl: './interview-page.html',
  styleUrl: './interview-page.css'
})
export class InterviewPage {
  constructor(private userContext: UserContext) {
    this.userContext.setRole('student');
  }
}
