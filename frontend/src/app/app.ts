import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';

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
  sessionState: 'checking' | 'authenticated' | 'unauthenticated' | 'degraded' = 'checking';
  isRetryingSession = false;
  private readonly sessionNoticeService: SessionNoticeService;
  private readonly authService: AuthService;

  constructor(
    authService: AuthService,
    userContext: UserContext,
    sessionNoticeService: SessionNoticeService,
    sessionInactivityService: SessionInactivityService
  ) {
    this.authService = authService;
    this.sessionNoticeService = sessionNoticeService;
    userContext.setUser(null);
    authService.ensureInitialized().subscribe(() => {
      userContext.setUser(authService.getCurrentUser());
    });

    sessionNoticeService.message$.subscribe((message) => {
      this.noticeMessage = message;
    });

    authService.sessionState$.subscribe((state) => {
      this.sessionState = state;
    });

    sessionInactivityService.init();
  }

  dismissNotice(): void {
    this.sessionNoticeService.clearMessage();
  }

  retrySessionValidation(): void {
    if (this.isRetryingSession) {
      return;
    }

    this.isRetryingSession = true;
    this.authService
      .retrySessionValidation()
      .pipe(
        finalize(() => {
          this.isRetryingSession = false;
        })
      )
      .subscribe();
  }
}
