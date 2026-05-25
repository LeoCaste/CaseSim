import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-session-completed-page',
  imports: [RouterLink],
  templateUrl: './session-completed-page.html',
  styleUrl: './session-completed-page.css'
})
export class SessionCompletedPage {
  constructor(private userContext: UserContext) {
    this.userContext.setRole('student');
  }
}
