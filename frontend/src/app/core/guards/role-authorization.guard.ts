import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Route, Router, UrlTree } from '@angular/router';
import { map, Observable } from 'rxjs';

import { UserRole } from '../models/auth-user.model';
import { AuthService } from '../services/auth.service';
import { UserContext } from '../services/user-context';

const ROLE_DATA_KEY = 'roles';

function redirectForCurrentSession(userRole: UserRole | null, router: Router): UrlTree {
  if (!userRole) {
    return router.createUrlTree(['/login']);
  }

  if (userRole === 'student') {
    return router.createUrlTree(['/student/dashboard']);
  }

  if (userRole === 'admin') {
    return router.createUrlTree(['/admin/llm-config']);
  }

  return router.createUrlTree(['/professor/dashboard']);
}

function readAllowedRoles(route: Pick<Route, 'data'>): UserRole[] {
  const configuredRoles = route.data?.[ROLE_DATA_KEY];
  if (!Array.isArray(configuredRoles)) {
    return [];
  }

  return configuredRoles.filter(
    (role): role is UserRole => role === 'student' || role === 'professor' || role === 'admin'
  );
}

function isRoleAllowed(userRole: UserRole, allowedRoles: UserRole[]): boolean {
  if (allowedRoles.includes(userRole)) {
    return true;
  }

  return userRole === 'admin' && allowedRoles.includes('professor');
}

function authorizeRoute(route: Pick<Route, 'data'>): Observable<boolean | UrlTree> {
  const authService = inject(AuthService);
  const userContext = inject(UserContext);
  const router = inject(Router);

  return authService.ensureInitialized().pipe(
    map(() => {
      const user = authService.getCurrentUser();
      const isValidatedSession = authService.isSessionValidated();
      userContext.setUser(user);

      if (!user || !isValidatedSession) {
        return redirectForCurrentSession(null, router);
      }

      const allowedRoles = readAllowedRoles(route);
      if (allowedRoles.length === 0 || isRoleAllowed(user.role, allowedRoles)) {
        return true;
      }

      return redirectForCurrentSession(user.role, router);
    })
  );
}

export const roleAuthorizationCanActivate: CanActivateFn = (route) => authorizeRoute(route.routeConfig ?? {});

export const roleAuthorizationCanMatch: CanMatchFn = (route) => authorizeRoute(route);
