import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, catchError, finalize, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

import { AuthUser } from '../models/auth-user.model';
import {
  AuthLoginRequest,
  AuthPreCheckRequest,
  BootstrapAdminRequest,
  BootstrapStatusResponse,
  ForgotPasswordRequest,
  ResetPasswordRequest
} from '../models/auth-flow.model';
import { environment } from '../../../environments/environment';

const AUTH_STORAGE_KEY = 'casesim.auth.user';
const AUTH_TOKEN_STORAGE_KEY = 'casesim.auth.token';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private currentUser: AuthUser | null = null;
  private token: string | null = null;
  private initialized = false;
  private initializing$: Observable<void> | null = null;
  private sessionValidated = false;
  private bootstrapStatusCache: BootstrapStatusResponse | null = null;
  private readonly authReadySubject = new BehaviorSubject<boolean>(false);
  private readonly backendAvailableSubject = new BehaviorSubject<boolean>(true);

  constructor(
    private http: HttpClient,
    private router: Router
  ) {
    this.currentUser = this.loadStoredUser();
    this.token = this.loadStoredToken();
  }

  ensureInitialized(): Observable<void> {
    if (this.initialized) {
      return of(void 0);
    }

    if (this.initializing$) {
      return this.initializing$;
    }

    if (!this.token) {
      this.initialized = true;
      this.sessionValidated = false;
      this.backendAvailableSubject.next(true);
      this.authReadySubject.next(true);
      return of(void 0);
    }

    this.initializing$ = this.me().pipe(
      map(() => void 0),
      tap(() => {
        this.initialized = true;
        this.authReadySubject.next(true);
      }),
      catchError(() => {
        this.initialized = true;
        this.authReadySubject.next(true);
        return of(void 0);
      }),
      finalize(() => {
        this.initializing$ = null;
      }),
      shareReplay(1)
    );

    return this.initializing$;
  }

  preCheck(email: string): Observable<{ requiresPassword: boolean }> {
    const normalizedEmail = email.trim().toLowerCase();

    if (!environment.useMocks) {
      const payload: AuthPreCheckRequest = { email: normalizedEmail };
      return this.http.post<{ requiresPassword?: unknown }>(`${this.apiBaseUrl}/auth/pre-check`, payload).pipe(
        map((response) => ({
          requiresPassword: response.requiresPassword === true
        }))
      );
    }

    const role = this.inferMockRole(normalizedEmail);
    return of({ requiresPassword: role === 'admin' });
  }

  bootstrapStatus(forceRefresh = false): Observable<BootstrapStatusResponse> {
    if (!forceRefresh && this.bootstrapStatusCache) {
      return of(this.bootstrapStatusCache);
    }

    if (!environment.useMocks) {
      return this.http.get<BootstrapStatusResponse>(`${this.apiBaseUrl}/auth/bootstrap-status`).pipe(
        map((response) => ({ needsInitialSetup: response.needsInitialSetup === true })),
        tap((status) => {
          this.bootstrapStatusCache = status;
        })
      );
    }

    const status = { needsInitialSetup: false };
    this.bootstrapStatusCache = status;
    return of(status);
  }

  bootstrapAdmin(request: BootstrapAdminRequest): Observable<void> {
    if (!environment.useMocks) {
      return this.http.post<void>(`${this.apiBaseUrl}/auth/bootstrap-admin`, request).pipe(
        tap(() => {
          this.bootstrapStatusCache = { needsInitialSetup: false };
        })
      );
    }

    this.bootstrapStatusCache = { needsInitialSetup: false };
    return of(void 0);
  }

  forgotPassword(request: ForgotPasswordRequest): Observable<void> {
    const payload = { email: request.email.trim().toLowerCase() };

    if (!environment.useMocks) {
      return this.http.post<void>(`${this.apiBaseUrl}/auth/forgot-password`, payload);
    }

    return of(void 0);
  }

  resetPassword(request: ResetPasswordRequest): Observable<void> {
    if (!environment.useMocks) {
      return this.http.post<void>(`${this.apiBaseUrl}/auth/reset-password`, request);
    }

    return of(void 0);
  }

  login(request: AuthLoginRequest): Observable<AuthUser> {
    const normalizedEmail = request.email.trim().toLowerCase();

    if (!environment.useMocks) {
      return this.http
        .post<BackendLoginResponse>(`${this.apiBaseUrl}/auth/login`, {
          email: normalizedEmail,
          ...(request.password !== undefined ? { password: request.password } : {})
        })
        .pipe(
          map((response) => {
            const user = this.mapBackendUser(response.user);
            this.currentUser = user;
            this.token = response.token;
            this.sessionValidated = true;
            this.persistUser(user);
            this.persistToken(response.token);
            return user;
          })
        );
    }

    const role = this.inferMockRole(normalizedEmail);
    if (!role) {
      return throwError(() => new Error('UNAUTHORIZED'));
    }

    if (role === 'admin' && !request.password?.trim()) {
      return throwError(() => new Error('UNAUTHORIZED'));
    }

    const roleLabelMap: Record<AuthUser['role'], string> = {
      admin: 'Administrador',
      professor: 'Docente',
      student: 'Estudiante'
    };

    this.currentUser = {
      id: role === 'admin' ? 'admin-01' : role === 'professor' ? 'prof-01' : 'stud-01',
      fullName: `${roleLabelMap[role]} CaseSim`,
      email: normalizedEmail,
      role
    };

    this.token = 'mock-token';
    this.sessionValidated = true;

    this.persistUser(this.currentUser);
    this.persistToken(this.token);

    return of(this.currentUser);
  }

  me(): Observable<AuthUser | null> {
    if (!environment.useMocks) {
      if (!this.token) {
        this.backendAvailableSubject.next(true);
        return of(null);
      }

      return this.http.get<BackendMeResponse | BackendUser>(`${this.apiBaseUrl}/auth/me`).pipe(
        map((response) => {
          const backendUser = 'user' in response ? response.user : response;
          const user = this.mapBackendUser(backendUser);
          this.currentUser = user;
          this.sessionValidated = true;
          this.persistUser(user);
          this.backendAvailableSubject.next(true);
          return user;
        }),
        catchError((error) => {
          if (this.isUnauthorizedError(error)) {
            this.clearSessionAndRedirectToLogin();
            this.backendAvailableSubject.next(true);
            return of(null);
          }

          if (this.token && this.currentUser) {
            this.sessionValidated = true;
            this.backendAvailableSubject.next(!this.isNetworkError(error));
            return of(this.currentUser);
          }

          this.backendAvailableSubject.next(false);
          return of(null);
        })
      );
    }

    return of(this.getCurrentUser());
  }

  getCurrentUser(): AuthUser | null {
    if (!this.currentUser) {
      this.currentUser = this.loadStoredUser();
    }

    return this.currentUser;
  }

  logout(): Observable<void> {
    if (!environment.useMocks) {
      return this.http.post<void>(`${this.apiBaseUrl}/auth/logout`, {}).pipe(
        catchError(() => of(void 0)),
        finalize(() => {
          this.clearSessionAndRedirectToLogin();
        }),
        map(() => {
          return void 0;
        })
      );
    }

    this.clearSessionAndRedirectToLogin();
    return of(void 0);
  }

  getToken(): string | null {
    return this.token;
  }

  isAuthReady(): boolean {
    return this.authReadySubject.value;
  }

  isBackendAvailable(): boolean {
    return this.backendAvailableSubject.value;
  }

  isSessionValidated(): boolean {
    return this.sessionValidated;
  }

  clearSessionByUnauthorized(): void {
    this.clearSessionAndRedirectToLogin();
    this.backendAvailableSubject.next(true);
  }

  clearSessionByConnectionFailure(): void {
    if (this.token && this.currentUser) {
      this.sessionValidated = true;
    }
    this.backendAvailableSubject.next(false);
  }

  private loadStoredUser(): AuthUser | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      const parsed = JSON.parse(raw) as Partial<AuthUser>;
      if (!parsed.id || !parsed.fullName || !parsed.email) {
        return null;
      }

      if (parsed.role !== 'student' && parsed.role !== 'professor' && parsed.role !== 'admin') {
        return null;
      }

      return {
        id: parsed.id,
        fullName: parsed.fullName,
        email: parsed.email,
        role: parsed.role
      };
    } catch {
      return null;
    }
  }

  private persistUser(user: AuthUser): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
  }

  private clearStoredUser(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.removeItem(AUTH_STORAGE_KEY);
  }

  private loadStoredToken(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
  }

  private persistToken(token: string): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);
  }

  private clearStoredToken(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  }

  private clearAuthState(): void {
    this.currentUser = null;
    this.token = null;
    this.sessionValidated = false;
    this.clearStoredUser();
    this.clearStoredToken();
  }

  private clearSessionAndRedirectToLogin(): void {
    this.clearAuthState();
    void this.router.navigate(['/login']);
  }

  private isUnauthorizedError(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  private isNetworkError(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 0;
  }

  private inferMockRole(email: string): AuthUser['role'] | null {
    const institutionalEmail = /^[^@\s]+@(ufromail\.cl|ufrontera\.cl)$/i.test(email);
    if (!institutionalEmail) {
      return null;
    }

    const [localPart, domain] = email.split('@');
    if (localPart.toLowerCase().includes('admin')) {
      return 'admin';
    }

    return domain.toLowerCase() === 'ufrontera.cl' ? 'professor' : 'student';
  }

  private mapBackendUser(user: BackendUser): AuthUser {
    return {
      id: user.id,
      fullName: user.name,
      email: user.email,
      role: this.mapBackendRole(user.roles)
    };
  }

  private mapBackendRole(roles: string[]): AuthUser['role'] {
    if (roles.includes('ADMIN')) {
      return 'admin';
    }

    // Aceptar ambos formatos por compatibilidad tras refactor
    if (roles.includes('PROFESOR') || roles.includes('PROFESSOR')) {
      return 'professor';
    }

    return 'student';
  }
}

interface BackendUser {
  id: string;
  name: string;
  email: string;
  roles: string[];
}

interface BackendLoginResponse {
  token: string;
  user: BackendUser;
}

interface BackendMeResponse {
  user: BackendUser;
}
