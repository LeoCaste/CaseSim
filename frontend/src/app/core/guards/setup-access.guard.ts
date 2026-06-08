import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const setupAccessGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.bootstrapStatus(true).pipe(
    map((status) => {
      if (status.adminExists) {
        return router.createUrlTree(['/login']);
      }

      return true;
    }),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
