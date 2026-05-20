import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from './core/services/auth.service';
import { SessionInactivityService } from './core/services/session-inactivity.service';
import { SessionNoticeService } from './core/services/session-notice.service';
import { UserContext } from './core/services/user-context';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');
  noticeMessage = '';
  private readonly sessionNoticeService: SessionNoticeService;

  constructor(
    authService: AuthService,
    userContext: UserContext,
    sessionNoticeService: SessionNoticeService,
    sessionInactivityService: SessionInactivityService
  ) {
    this.sessionNoticeService = sessionNoticeService;
    userContext.setUser(null);
    authService.ensureInitialized().subscribe(() => {
      userContext.setUser(authService.getCurrentUser());
    });

    sessionNoticeService.message$.subscribe((message) => {
      this.noticeMessage = message;
    });

    sessionInactivityService.init();
  }

  dismissNotice(): void {
    this.sessionNoticeService.clearMessage();
  }
}
