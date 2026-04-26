import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-waiting-room-page',
  imports: [RouterLink],
  templateUrl: './waiting-room-page.html',
  styleUrl: './waiting-room-page.css',
})
export class WaitingRoomPage {
  constructor(private userContext: UserContext) {
    this.userContext.setRole('student');
  }
}
