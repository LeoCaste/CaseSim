import { HttpInterceptorFn } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();
  const isAuthFlowRequest = /\/auth\/(login|pre-check)$/i.test(req.url);
  const isSessionValidationRequest = /\/auth\/me$/i.test(req.url);
  const isProtectedRequest = !isAuthFlowRequest;

  if (isAuthFlowRequest) {
    return next(req);
  }

  const request = token
    ? req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    })
    : req;

  return next(request).pipe(
    catchError((error) => {
      if (
        error instanceof HttpErrorResponse &&
        (error.status === 401 || error.status === 403) &&
        isSessionValidationRequest
      ) {
        authService.clearSessionByUnauthorized();
      }

      if (error instanceof HttpErrorResponse && error.status === 0 && isProtectedRequest) {
        authService.clearSessionByConnectionFailure();
      }

      return throwError(() => error);
    })
  );
};
