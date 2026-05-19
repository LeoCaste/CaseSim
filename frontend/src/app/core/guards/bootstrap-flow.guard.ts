import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../services/auth.service';

function isSetupSafeRoute(url: string): boolean {
  return url.startsWith('/setup') || url.startsWith('/reset-password');
}

export const bootstrapFlowGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (isSetupSafeRoute(state.url)) {
    return of(true);
  }

  return authService.bootstrapStatus(true).pipe(
    map((status) => {
      if (status.needsInitialSetup && !isSetupSafeRoute(state.url)) {
        return router.createUrlTree(['/setup']);
      }

      return true;
    }),
    catchError(() => of(true))
  );
};
