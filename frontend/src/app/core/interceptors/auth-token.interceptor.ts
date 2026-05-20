import { HttpInterceptorFn } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = normalizeToken(authService.getToken());
  const isAuthFlowRequest = /\/auth\/(login|pre-check|forgot-password|reset-password|bootstrap-status|bootstrap-admin)$/i.test(req.url);
  const isSessionValidationRequest = /\/auth\/me$/i.test(req.url);
  const isProtectedRequest = !isAuthFlowRequest;

  if (isAuthFlowRequest) {
    return next(req);
  }

  const request = token && !req.headers.has('Authorization')
    ? req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    })
    : req;

  return next(request).pipe(
    catchError((error) => {
      const authCode = extractAuthErrorCode(error);

      if (error instanceof HttpErrorResponse && error.status === 401 && isProtectedRequest) {
        authService.clearSessionByUnauthorized();
      }

      if (
        error instanceof HttpErrorResponse
        && error.status === 403
        && isProtectedRequest
        && authCode === 'AUTH_FORBIDDEN'
      ) {
        authService.handleForbidden();
      }

      if (error instanceof HttpErrorResponse && error.status === 0 && isProtectedRequest && !isSessionValidationRequest) {
        authService.clearSessionByConnectionFailure();
      }

      return throwError(() => error);
    })
  );
};

function extractAuthErrorCode(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }

  const payload = error.error as { code?: unknown; error?: { code?: unknown } } | null | undefined;

  if (!payload || typeof payload !== 'object') {
    return null;
  }

  if (typeof payload.code === 'string' && payload.code.trim()) {
    return payload.code.trim().toUpperCase();
  }

  if (typeof payload.error?.code === 'string' && payload.error.code.trim()) {
    return payload.error.code.trim().toUpperCase();
  }

  return null;
}

function normalizeToken(rawToken: string | null): string | null {
  if (!rawToken) {
    return null;
  }

  const trimmedToken = rawToken.trim();
  if (!trimmedToken) {
    return null;
  }

  const bearerPrefixPattern = /^Bearer\s+/i;
  return trimmedToken.replace(bearerPrefixPattern, '').trim() || null;
}
