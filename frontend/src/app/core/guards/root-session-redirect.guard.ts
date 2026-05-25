import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { UserContext } from '../services/user-context';

export const rootSessionRedirectGuard: CanActivateFn = () => {
  const router = inject(Router);
  const userContext = inject(UserContext);
  const authService = inject(AuthService);

  return authService.ensureInitialized().pipe(
    map(() => {
      const user = authService.getCurrentUser();
      const isValidatedSession = authService.isSessionValidated();
      userContext.setUser(user);

      if (!isValidatedSession) {
        return router.createUrlTree(['/login']);
      }

      if (user?.role === 'admin') {
        return router.createUrlTree(['/admin/llm-config']);
      }

      if (user?.role === 'professor') {
        return router.createUrlTree(['/professor/dashboard']);
      }

      if (user?.role === 'student') {
        return router.createUrlTree(['/student/dashboard']);
      }

      return router.createUrlTree(['/login']);
    })
  );
};
