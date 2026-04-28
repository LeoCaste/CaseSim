import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of, throwError } from 'rxjs';

import { AuthUser } from '../models/auth-user.model';
import { AuthLoginRequest, AuthPreCheckRequest, AuthPreCheckResponse } from '../models/auth-flow.model';
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

  constructor(private http: HttpClient) {
    this.currentUser = this.loadStoredUser();
    this.token = this.loadStoredToken();
  }

  preCheck(email: string): Observable<AuthPreCheckResponse> {
    const normalizedEmail = email.trim().toLowerCase();

    if (!environment.useMocks) {
      const payload: AuthPreCheckRequest = { email: normalizedEmail };
      return this.http.post<AuthPreCheckResponse>(`${this.apiBaseUrl}/auth/pre-check`, payload);
    }

    const role = this.inferMockRole(normalizedEmail);
    return of({ requiresPassword: role === 'admin' });
  }

  login(request: AuthLoginRequest): Observable<AuthUser> {
    const normalizedEmail = request.email.trim().toLowerCase();

    if (!environment.useMocks) {
      return this.http
        .post<BackendLoginResponse>(`${this.apiBaseUrl}/auth/login`, {
          email: normalizedEmail,
          ...(request.password ? { password: request.password } : {})
        })
        .pipe(
          map((response) => {
            const user = this.mapBackendUser(response.user);
            this.currentUser = user;
            this.token = response.token;
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

    this.persistUser(this.currentUser);
    this.persistToken(this.token);

    return of(this.currentUser);
  }

  me(): Observable<AuthUser | null> {
    if (!environment.useMocks) {
      if (!this.token) {
        return of(null);
      }

      return this.http.get<BackendMeResponse | BackendUser>(`${this.apiBaseUrl}/auth/me`).pipe(
        map((response) => {
          const backendUser = 'user' in response ? response.user : response;
          const user = this.mapBackendUser(backendUser);
          this.currentUser = user;
          this.persistUser(user);
          return user;
        }),
        catchError(() => {
          this.clearAuthState();
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
        map(() => {
          this.clearAuthState();
          return void 0;
        })
      );
    }

    this.clearAuthState();
    return of(void 0);
  }

  getToken(): string | null {
    return this.token;
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
    this.clearStoredUser();
    this.clearStoredToken();
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

    if (roles.includes('PROFESOR')) {
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
