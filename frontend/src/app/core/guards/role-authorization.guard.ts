import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Route, Router, UrlTree } from '@angular/router';

import { UserRole } from '../models/auth-user.model';
import { AuthService } from '../services/auth.service';
import { UserContext } from '../services/user-context';

const ROLE_DATA_KEY = 'roles';

function redirectForCurrentSession(userRole: UserRole | null, router: Router): UrlTree {
  if (!userRole) {
    return router.createUrlTree(['/login']);
  }

  return router.createUrlTree([userRole === 'professor' ? '/professor/dashboard' : '/student/dashboard']);
}

function readAllowedRoles(route: Pick<Route, 'data'>): UserRole[] {
  const configuredRoles = route.data?.[ROLE_DATA_KEY];
  if (!Array.isArray(configuredRoles)) {
    return [];
  }

  return configuredRoles.filter((role): role is UserRole => role === 'student' || role === 'professor');
}

function authorizeRoute(route: Pick<Route, 'data'>): boolean | UrlTree {
  const authService = inject(AuthService);
  const userContext = inject(UserContext);
  const router = inject(Router);

  const user = authService.getCurrentUser();
  userContext.setUser(user);

  if (!user) {
    return redirectForCurrentSession(null, router);
  }

  const allowedRoles = readAllowedRoles(route);
  if (allowedRoles.length === 0 || allowedRoles.includes(user.role)) {
    return true;
  }

  return redirectForCurrentSession(user.role, router);
}

export const roleAuthorizationCanActivate: CanActivateFn = (route) => authorizeRoute(route.routeConfig ?? {});

export const roleAuthorizationCanMatch: CanMatchFn = (route) => authorizeRoute(route);
