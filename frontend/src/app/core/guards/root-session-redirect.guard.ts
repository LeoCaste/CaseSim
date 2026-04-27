import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { UserContext } from '../services/user-context';

export const rootSessionRedirectGuard: CanActivateFn = () => {
  const router = inject(Router);
  const userContext = inject(UserContext);
  const authService = inject(AuthService);
  const user = authService.getCurrentUser();

  userContext.setUser(user);

  if (user?.role === 'professor' || user?.role === 'admin') {
    return router.createUrlTree(['/professor/dashboard']);
  }

  if (user?.role === 'student') {
    return router.createUrlTree(['/student/dashboard']);
  }

  return router.createUrlTree(['/login']);
};
