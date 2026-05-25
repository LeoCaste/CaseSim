import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class AuthNavigationService {
  private redirectInProgress = false;

  constructor(private router: Router) {}

  redirectToLogin(reason?: 'expired'): void {
    const currentUrl = this.router.url;
    if (currentUrl.startsWith('/login') || this.redirectInProgress) {
      return;
    }

    this.redirectInProgress = true;
    void this.router
      .navigate(['/login'], {
        queryParams: reason ? { reason } : undefined,
        replaceUrl: true
      })
      .finally(() => {
        this.redirectInProgress = false;
      });
  }
}
