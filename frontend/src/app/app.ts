import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from './core/services/auth.service';
import { UserContext } from './core/services/user-context';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');

  constructor(authService: AuthService, userContext: UserContext) {
    userContext.setUser(authService.getCurrentUser());
    authService.me().subscribe((user) => userContext.setUser(user));
  }
}
