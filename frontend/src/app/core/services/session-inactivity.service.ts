import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, fromEvent, merge, Subscription, throttleTime } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

const DEFAULT_IDLE_TIMEOUT_MS = 900000;

@Injectable({
  providedIn: 'root'
})
export class SessionInactivityService {
  private readonly idleTimeoutMs = environment.authIdleTimeoutMs ?? DEFAULT_IDLE_TIMEOUT_MS;
  private readonly debugEnabled =
    !environment.production && typeof localStorage !== 'undefined' && localStorage.getItem('casesim.debug.afk') === '1';
  private initialized = false;
  private activitySubscription: Subscription | null = null;
  private tokenSubscription: Subscription | null = null;
  private storageSubscription: Subscription | null = null;
  private idleTimer: ReturnType<typeof setTimeout> | null = null;
  private tokenExpirationTimer: ReturnType<typeof setTimeout> | null = null;
  private expirationTriggered = false;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  init(): void {
    if (this.initialized || typeof window === 'undefined') {
      return;
    }

    this.initialized = true;
    this.debug(`Init AFK idle timeout: ${this.idleTimeoutMs}ms`);

    const navigationActivity$ = this.router.events.pipe(filter((event) => event instanceof NavigationEnd));
    const mouseMoveActivity$ = fromEvent(document, 'mousemove').pipe(throttleTime(1000));
    const userActivity$ = merge(fromEvent(document, 'click'), fromEvent(document, 'keydown'), mouseMoveActivity$);

    this.activitySubscription = merge(navigationActivity$, userActivity$).subscribe(() => {
      this.onActivity();
    });

    this.tokenSubscription = this.authService.tokenChanges$.subscribe((token) => {
      this.onTokenChange(token);
    });

    this.storageSubscription = fromEvent<StorageEvent>(window, 'storage').subscribe((event) => {
      if (!event.key || (event.key !== 'casesim.auth.token' && event.key !== 'casesim.auth.user')) {
        return;
      }

      this.authService.syncSessionFromStorage();
      this.onTokenChange(this.authService.getToken());
      this.debug('Storage event synced auth session');
    });

    this.onTokenChange(this.authService.getToken());
    this.onActivity();
  }

  private onActivity(): void {
    const token = this.authService.getToken();
    if (!token) {
      this.stopAllTimers();
      this.expirationTriggered = false;
      return;
    }

    this.expirationTriggered = false;
    this.debug('Activity detected, restarting idle timer');
    this.startIdleTimer();
  }

  private onTokenChange(token: string | null): void {
    if (!token) {
      this.debug('Token removed, stopping AFK timers');
      this.stopAllTimers();
      this.expirationTriggered = false;
      return;
    }

    this.debug('Token detected, scheduling AFK and token-exp timers');
    this.scheduleTokenExpiration(token);
    this.startIdleTimer();
  }

  private startIdleTimer(): void {
    this.stopIdleTimer();
    this.debug(`Idle timer scheduled in ${this.idleTimeoutMs}ms`);
    this.idleTimer = setTimeout(() => {
      if (this.expirationTriggered || !this.authService.getToken()) {
        return;
      }

      this.expirationTriggered = true;
      this.debug('Idle timer fired, expiring session by inactivity');
      this.authService.expireSessionByInactivity();
      this.stopAllTimers();
    }, this.idleTimeoutMs);
  }

  private scheduleTokenExpiration(token: string): void {
    this.stopTokenExpirationTimer();

    if (environment.useMocks) {
      this.debug('useMocks=true, skipping token exp scheduling');
      return;
    }

    const expirationAtMs = this.resolveTokenExpirationMs(token);
    if (expirationAtMs === null) {
      this.debug('Invalid JWT exp payload detected, expiring session');
      this.expireByInvalidToken();
      return;
    }

    const timeoutMs = expirationAtMs - Date.now();
    if (timeoutMs <= 0) {
      this.debug('Token already expired by exp claim');
      this.expireByInvalidToken();
      return;
    }

    this.debug(`Token expiration timer scheduled in ${timeoutMs}ms`);
    this.tokenExpirationTimer = setTimeout(() => {
      if (this.expirationTriggered || !this.authService.getToken()) {
        return;
      }

      this.expirationTriggered = true;
      this.debug('Token expiration timer fired');
      this.authService.expireSessionByInvalidToken();
      this.stopAllTimers();
    }, timeoutMs);
  }

  private resolveTokenExpirationMs(token: string): number | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) {
        return null;
      }

      const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
      const decoded = atob(padded);
      const payload = JSON.parse(decoded) as { exp?: unknown };

      if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) {
        return null;
      }

      return payload.exp * 1000;
    } catch {
      return null;
    }
  }

  private expireByInvalidToken(): void {
    if (this.expirationTriggered || !this.authService.getToken()) {
      return;
    }

    this.expirationTriggered = true;
    this.authService.expireSessionByInvalidToken();
    this.stopAllTimers();
  }

  private stopIdleTimer(): void {
    if (!this.idleTimer) {
      return;
    }

    clearTimeout(this.idleTimer);
    this.idleTimer = null;
  }

  private stopTokenExpirationTimer(): void {
    if (!this.tokenExpirationTimer) {
      return;
    }

    clearTimeout(this.tokenExpirationTimer);
    this.tokenExpirationTimer = null;
  }

  private stopAllTimers(): void {
    this.stopIdleTimer();
    this.stopTokenExpirationTimer();
  }

  private debug(message: string): void {
    if (!this.debugEnabled) {
      return;
    }

    console.debug(`[AFK] ${message}`);
  }
}
