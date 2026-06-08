import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, catchError, finalize, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthUser } from '../models/auth-user.model';
import {
  AuthLoginRequest,
  AuthPreCheckRequest,
  BootstrapAdminRequest,
  BootstrapAdminStatusResponse,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  ResetPasswordRequest
} from '../models/auth-flow.model';
import { environment } from '../../../environments/environment';
import { JwtStorageService } from './jwt-storage.service';
import { AuthNavigationService } from './auth-navigation.service';
import { SessionNoticeService } from './session-notice.service';

export type AuthSessionState = 'checking' | 'authenticated' | 'unauthenticated' | 'degraded';

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
  private bootstrapStatusCache: BootstrapAdminStatusResponse | null = null;
  private readonly authReadySubject = new BehaviorSubject<boolean>(false);
  private readonly backendAvailableSubject = new BehaviorSubject<boolean>(true);
  private readonly sessionStateSubject = new BehaviorSubject<AuthSessionState>('checking');
  private logoutInProgress = false;
  private readonly tokenStateSubject = new BehaviorSubject<string | null>(null);

  constructor(
    private http: HttpClient,
    private jwtStorageService: JwtStorageService,
    private authNavigationService: AuthNavigationService,
    private sessionNoticeService: SessionNoticeService
  ) {
    this.currentUser = this.loadStoredUser();
    this.token = this.loadStoredToken();
    this.tokenStateSubject.next(this.token);
  }

  get tokenChanges$(): Observable<string | null> {
    return this.tokenStateSubject.asObservable();
  }

  get sessionState$(): Observable<AuthSessionState> {
    return this.sessionStateSubject.asObservable();
  }

  ensureInitialized(): Observable<void> {
    if (this.initialized) {
      return of(void 0);
    }

    if (this.initializing$) {
      return this.initializing$;
    }

    this.initializing$ = this.validateSessionOnAppStart().pipe(
      finalize(() => {
        this.initializing$ = null;
      }),
      shareReplay(1)
    );

    return this.initializing$;
  }

  validateSessionOnAppStart(): Observable<void> {
    this.sessionStateSubject.next('checking');

    if (environment.useMocks) {
      this.sessionValidated = !!this.token && !!this.currentUser;
      this.sessionStateSubject.next(this.sessionValidated ? 'authenticated' : 'unauthenticated');
      this.backendAvailableSubject.next(true);
      this.initialized = true;
      this.authReadySubject.next(true);
      return of(void 0);
    }

    if (!this.token) {
      this.initialized = true;
      this.sessionValidated = false;
      this.sessionStateSubject.next('unauthenticated');
      this.backendAvailableSubject.next(true);
      this.authReadySubject.next(true);
      return of(void 0);
    }

    return this.http.get<BackendMeResponse | BackendUser>(`${this.apiBaseUrl}/auth/me`).pipe(
      tap((response) => {
        const backendUser = 'user' in response ? response.user : response;
        const user = this.mapBackendUser(backendUser);
        this.currentUser = user;
        this.sessionValidated = true;
        this.sessionStateSubject.next('authenticated');
        this.persistUser(user);
        this.backendAvailableSubject.next(true);
      }),
      map(() => void 0),
      catchError((error) => {
        const decision = this.resolveAuthErrorDecision(error);

        if (decision === 'unauthorized') {
          this.handleUnauthorizedSession();
          this.backendAvailableSubject.next(true);
          this.sessionStateSubject.next('unauthenticated');
          return of(void 0);
        }

        if (decision === 'forbidden') {
          this.sessionValidated = !!this.currentUser;
          this.sessionStateSubject.next(this.currentUser ? 'authenticated' : 'degraded');
          this.backendAvailableSubject.next(true);
          this.sessionNoticeService.setMessage('No tienes permisos para acceder.');
          return of(void 0);
        }

        if (decision === 'degraded') {
          this.sessionValidated = !!this.currentUser;
          this.sessionStateSubject.next('degraded');
          this.backendAvailableSubject.next(false);
          this.sessionNoticeService.setMessage('No se pudo conectar con el servidor. Reintenta en unos segundos.');
          return of(void 0);
        }

        this.sessionValidated = !!this.currentUser;
        this.sessionStateSubject.next(this.currentUser ? 'authenticated' : 'unauthenticated');
        this.backendAvailableSubject.next(true);
        return of(void 0);
      }),
      finalize(() => {
        this.initialized = true;
        this.authReadySubject.next(true);
      })
    );
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

  bootstrapStatus(forceRefresh = false): Observable<BootstrapAdminStatusResponse> {
    if (!forceRefresh && this.bootstrapStatusCache) {
      return of(this.bootstrapStatusCache);
    }

    if (!environment.useMocks) {
      return this.http.get<BootstrapAdminStatusResponse>(`${this.apiBaseUrl}/bootstrap/admin/status`).pipe(
        map((response) => ({ adminExists: response.adminExists === true })),
        tap((status) => {
          this.bootstrapStatusCache = status;
        })
      );
    }

    const status = { adminExists: true };
    this.bootstrapStatusCache = status;
    return of(status);
  }

  bootstrapAdmin(request: BootstrapAdminRequest): Observable<void> {
    if (!environment.useMocks) {
      return this.http.post<void>(`${this.apiBaseUrl}/bootstrap/admin`, request).pipe(
        tap(() => {
          this.bootstrapStatusCache = { adminExists: true };
        })
      );
    }

    this.bootstrapStatusCache = { adminExists: true };
    return of(void 0);
  }

  forgotPassword(request: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    const payload = { email: request.email.trim().toLowerCase() };

    if (!environment.useMocks) {
      return this.http.post<ForgotPasswordResponse>(`${this.apiBaseUrl}/auth/forgot-password`, payload);
    }

    return of({});
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
            this.tokenStateSubject.next(this.token);
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
    this.tokenStateSubject.next(this.token);

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
          this.sessionStateSubject.next('authenticated');
          this.persistUser(user);
          this.backendAvailableSubject.next(true);
          return user;
        }),
        catchError((error) => {
          if (this.isUnauthorizedError(error)) {
            this.handleUnauthorizedSession();
            this.backendAvailableSubject.next(true);
            this.sessionStateSubject.next('unauthenticated');
            return of(null);
          }

          if (this.token && this.currentUser) {
            this.sessionValidated = true;
            this.backendAvailableSubject.next(!this.isNetworkError(error));
            this.sessionStateSubject.next(this.isNetworkError(error) ? 'degraded' : 'authenticated');
            return of(this.currentUser);
          }

          this.backendAvailableSubject.next(false);
          this.sessionStateSubject.next('degraded');
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
    if (this.logoutInProgress) {
      return of(void 0);
    }

    this.logoutInProgress = true;
    this.clearSession();
    this.authNavigationService.redirectToLogin();

    if (!environment.useMocks) {
      this.http
        .post<void>(`${this.apiBaseUrl}/auth/logout`, {})
        .pipe(
          catchError(() => of(void 0)),
          finalize(() => {
            this.logoutInProgress = false;
          })
        )
        .subscribe();

      return of(void 0);
    }

    this.logoutInProgress = false;
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
    this.handleUnauthorizedSession();
    this.backendAvailableSubject.next(true);
    this.sessionStateSubject.next('unauthenticated');
  }

  handleForbidden(): void {
    this.sessionNoticeService.setMessage('No tienes permisos para acceder.');
  }

  clearSessionByConnectionFailure(): void {
    if (this.token && this.currentUser) {
      this.sessionValidated = true;
    }
    this.sessionStateSubject.next('degraded');
    this.backendAvailableSubject.next(false);
  }

  private loadStoredUser(): AuthUser | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    const raw = this.jwtStorageService.getStoredUser();
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
    this.jwtStorageService.setStoredUser(JSON.stringify(user));
  }

  private clearStoredUser(): void {
    this.jwtStorageService.clearStoredUser();
  }

  private loadStoredToken(): string | null {
    return this.jwtStorageService.getStoredToken();
  }

  private persistToken(token: string): void {
    this.jwtStorageService.setStoredToken(token);
  }

  private clearStoredToken(): void {
    this.jwtStorageService.clearStoredToken();
  }

  private clearAuthState(): void {
    this.currentUser = null;
    this.token = null;
    this.sessionValidated = false;
    this.clearStoredUser();
    this.clearStoredToken();
    this.tokenStateSubject.next(this.token);
    this.sessionStateSubject.next('unauthenticated');
  }

  clearSession(): void {
    this.clearAuthState();
  }

  private handleUnauthorizedSession(): void {
    this.clearSession();
    this.logoutInProgress = false;
    this.sessionNoticeService.setMessage('Tu sesión expiró. Inicia sesión nuevamente.');
    this.authNavigationService.redirectToLogin('expired');
  }

  expireSessionByInactivity(): void {
    this.clearSession();
    this.logoutInProgress = false;
    this.sessionNoticeService.setMessage('Tu sesión expiró por inactividad. Inicia sesión nuevamente.');
    this.authNavigationService.redirectToLogin('expired');
  }

  expireSessionByInvalidToken(): void {
    this.clearSession();
    this.logoutInProgress = false;
    this.sessionNoticeService.setMessage('Tu sesión expiró. Inicia sesión nuevamente.');
    this.authNavigationService.redirectToLogin('expired');
  }

  syncSessionFromStorage(): void {
    const storedToken = this.loadStoredToken();

    if (storedToken === this.token) {
      return;
    }

    if (!storedToken) {
      this.clearSession();
      this.logoutInProgress = false;
      this.authNavigationService.redirectToLogin();
      return;
    }

    this.token = storedToken;
    this.currentUser = this.loadStoredUser();
    this.sessionValidated = this.currentUser !== null;
    this.tokenStateSubject.next(this.token);
  }

  private isUnauthorizedError(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  retrySessionValidation(): Observable<void> {
    this.initialized = false;
    return this.ensureInitialized();
  }

  private resolveAuthErrorDecision(error: unknown): 'unauthorized' | 'forbidden' | 'degraded' | 'unknown' {
    if (!(error instanceof HttpErrorResponse)) {
      return 'unknown';
    }

    const errorCode = this.extractAuthErrorCode(error);

    if (error.status === 401) {
      if (!errorCode || errorCode.startsWith('AUTH_')) {
        return 'unauthorized';
      }

      return 'unauthorized';
    }

    if (error.status === 403 && errorCode === 'AUTH_FORBIDDEN') {
      return 'forbidden';
    }

    if (error.status === 0 || error.status >= 500) {
      return 'degraded';
    }

    return 'unknown';
  }

  private extractAuthErrorCode(error: HttpErrorResponse): string | null {
    const payload = error.error as
      | { code?: unknown; error?: { code?: unknown } }
      | string
      | null
      | undefined;

    if (payload && typeof payload === 'object') {
      if (typeof payload.code === 'string' && payload.code.trim()) {
        return payload.code.trim().toUpperCase();
      }

      if (typeof payload.error?.code === 'string' && payload.error.code.trim()) {
        return payload.error.code.trim().toUpperCase();
      }
    }

    return null;
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
