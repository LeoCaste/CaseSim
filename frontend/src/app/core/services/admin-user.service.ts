import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable, of, throwError, TimeoutError, timeout } from 'rxjs';

import {
  AdminUser,
  AdminUserApiError,
  AdminUserCreatePayload,
  AdminUserRoleOption,
  AdminUserRole,
  AdminUserUpdatePayload
} from '../models/admin-user.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminUserService {
  private readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly deleteTimeoutMs = 8000;

  private mockUsers: AdminUser[] = [
    {
      id: 'admin-01',
      name: 'Administrador CaseSim',
      email: 'admin@casesim.cl',
      role: 'ADMIN',
      active: true
    },
    {
      id: 'prof-01',
      name: 'Valeria Contreras',
      email: 'valeria.contreras@ufrontera.cl',
      role: 'PROFESOR',
      active: true
    },
    {
      id: 'stud-01',
      name: 'Nicolás Muñoz',
      email: 'nicolas.munoz@ufromail.cl',
      role: 'ESTUDIANTE',
      active: false
    }
  ];

  constructor(private http: HttpClient) {}

  getRoles(): Observable<AdminUserRoleOption[]> {
    if (!environment.useMocks) {
      return this.http.get<BackendAdminUserRoleResponse[]>(`${this.apiBaseUrl}/admin/users/roles`).pipe(
        map((response) => response.map((role) => ({ code: role.code }))),
        catchError((error) => throwError(() => this.toApiError(error, 'No fue posible cargar roles.')))
      );
    }

    const uniqueRoles = Array.from(new Set(this.mockUsers.map((user) => user.role)));
    return of(uniqueRoles.map((code) => ({ code })));
  }

  getUsers(active: 'true' | 'false' | 'all' = 'true'): Observable<AdminUser[]> {
    if (!environment.useMocks) {
      const params = new HttpParams().set('active', active);
      return this.http.get<BackendAdminUserResponse[]>(`${this.apiBaseUrl}/admin/users`, { params }).pipe(
        map((response) => response.map((item) => this.mapBackendUser(item))),
        map((users) => this.sortUsers(users)),
        catchError((error) => throwError(() => this.toApiError(error, 'No fue posible cargar los usuarios.')))
      );
    }

    const filteredUsers = this.mockUsers.filter((user) => {
      if (active === 'all') {
        return true;
      }

      return active === 'true' ? user.active : !user.active;
    });

    return of(this.sortUsers(filteredUsers));
  }

  createUser(payload: AdminUserCreatePayload): Observable<AdminUser> {
    if (!environment.useMocks) {
      return this.http
        .post<BackendAdminUserResponse>(`${this.apiBaseUrl}/admin/users`, this.mapUpsertPayload(payload))
        .pipe(
          map((response) => this.mapBackendUser(response)),
          catchError((error) => throwError(() => this.toApiError(error, 'No fue posible crear el usuario.')))
        );
    }

    const normalizedEmail = payload.email.trim().toLowerCase();
    const emailAlreadyExists = this.mockUsers.some((user) => user.email.toLowerCase() === normalizedEmail);
    if (emailAlreadyExists) {
      return throwError(() =>
        this.buildApiError(409, 'CONFLICT', 'Ya existe un usuario registrado con ese correo electrónico.')
      );
    }

    const createdUser: AdminUser = {
      id: `user-${Date.now()}`,
      name: payload.name.trim(),
      email: normalizedEmail,
      role: payload.role,
      active: true
    };

    this.mockUsers = this.sortUsers([createdUser, ...this.mockUsers]);
    return of(createdUser);
  }

  updateUser(userId: string, payload: AdminUserUpdatePayload): Observable<AdminUser> {
    if (!environment.useMocks) {
      return this.http
        .put<BackendAdminUserResponse>(`${this.apiBaseUrl}/admin/users/${userId}`, this.mapUpsertPayload(payload))
        .pipe(
          map((response) => this.mapBackendUser(response)),
          catchError((error) => throwError(() => this.toApiError(error, 'No fue posible actualizar el usuario.')))
        );
    }

    const existingUser = this.mockUsers.find((user) => user.id === userId);
    if (!existingUser) {
      return throwError(() => this.buildApiError(400, 'VALIDATION', 'Usuario no encontrado para edición.'));
    }

    const normalizedEmail = payload.email.trim().toLowerCase();
    const emailAlreadyExists = this.mockUsers.some(
      (user) => user.id !== userId && user.email.toLowerCase() === normalizedEmail
    );

    if (emailAlreadyExists) {
      return throwError(() =>
        this.buildApiError(409, 'CONFLICT', 'Ya existe un usuario registrado con ese correo electrónico.')
      );
    }

    const updatedUser: AdminUser = {
      ...existingUser,
      name: payload.name.trim(),
      email: normalizedEmail,
      role: payload.role
    };

    this.mockUsers = this.sortUsers(this.mockUsers.map((user) => (user.id === userId ? updatedUser : user)));
    return of(updatedUser);
  }

  toggleStatus(userId: string, active: boolean): Observable<AdminUser> {
    if (!environment.useMocks) {
      return this.http
        .patch<BackendAdminUserResponse>(`${this.apiBaseUrl}/admin/users/${userId}/status`, { active })
        .pipe(
          map((response) => this.mapBackendUser(response)),
          catchError((error) => throwError(() => this.toApiError(error, 'No fue posible actualizar el estado.')))
        );
    }

    const existingUser = this.mockUsers.find((user) => user.id === userId);
    if (!existingUser) {
      return throwError(() => this.buildApiError(400, 'VALIDATION', 'Usuario no encontrado para cambio de estado.'));
    }

    const updatedUser: AdminUser = {
      ...existingUser,
      active
    };

    this.mockUsers = this.mockUsers.map((user) => (user.id === userId ? updatedUser : user));
    return of(updatedUser);
  }

  deleteUser(userId: string): Observable<void> {
    if (!environment.useMocks) {
      return this.http
        .delete<void>(`${this.apiBaseUrl}/admin/users/${userId}`)
        .pipe(
          timeout(this.deleteTimeoutMs),
          catchError((error) => throwError(() => this.toDeleteApiError(error)))
        );
    }

    const exists = this.mockUsers.some((user) => user.id === userId);
    if (!exists) {
      return throwError(() => this.buildApiError(400, 'VALIDATION', 'Usuario no encontrado para eliminación.'));
    }

    this.mockUsers = this.mockUsers.filter((user) => user.id !== userId);
    return of(void 0);
  }

  private mapUpsertPayload(payload: AdminUserCreatePayload | AdminUserUpdatePayload): BackendAdminUserUpsertRequest {
    return {
      name: payload.name.trim(),
      email: payload.email.trim().toLowerCase(),
      role: payload.role,
      password: payload.password?.trim() ? payload.password.trim() : null
    };
  }

  private mapBackendUser(user: BackendAdminUserResponse): AdminUser {
    const backendRole = user.roles.length > 0 ? user.roles[0] : 'ESTUDIANTE';

    return {
      id: user.id,
      name: user.name,
      email: user.email,
      role: this.mapBackendRole(backendRole),
      active: user.active
    };
  }

  private mapBackendRole(role: string): AdminUserRole {
    return role;
  }

  private sortUsers(users: AdminUser[]): AdminUser[] {
    return [...users].sort((left, right) => left.name.localeCompare(right.name, 'es'));
  }

  private toApiError(error: unknown, defaultMessage: string): AdminUserApiError {
    if (error instanceof HttpErrorResponse) {
      const backendMessage = this.extractBackendMessage(error.error);

      if (error.status === 400) {
        return this.buildApiError(400, 'VALIDATION', backendMessage ?? defaultMessage);
      }

      if (error.status === 401) {
        return this.buildApiError(401, 'UNAUTHORIZED', 'Tu sesión expiró. Vuelve a iniciar sesión.');
      }

      if (error.status === 403) {
        return this.buildApiError(403, 'FORBIDDEN', 'No tienes permisos para realizar esta acción.');
      }

      if (error.status === 409) {
        return this.buildApiError(409, 'CONFLICT', backendMessage ?? 'La operación entra en conflicto con un usuario existente.');
      }

      return this.buildApiError(error.status, 'UNKNOWN', backendMessage ?? defaultMessage);
    }

    return this.buildApiError(0, 'UNKNOWN', defaultMessage);
  }

  private toDeleteApiError(error: unknown): AdminUserApiError {
    if (error instanceof TimeoutError) {
      return this.buildApiError(
        408,
        'UNKNOWN',
        'La eliminación está tardando más de lo esperado. Revisa el estado del usuario y vuelve a intentar.'
      );
    }

    return this.toApiError(error, 'No fue posible eliminar el usuario.');
  }

  private extractBackendMessage(errorBody: unknown): string | null {
    if (typeof errorBody === 'string' && errorBody.trim().length > 0) {
      return errorBody.trim();
    }

    if (!errorBody || typeof errorBody !== 'object') {
      return null;
    }

    const maybeMessage = (errorBody as { message?: unknown }).message;
    return typeof maybeMessage === 'string' ? maybeMessage : null;
  }

  private buildApiError(status: number, code: AdminUserApiError['code'], message: string): AdminUserApiError {
    return { status, code, message };
  }
}

interface BackendAdminUserResponse {
  id: string;
  name: string;
  email: string;
  roles: string[];
  active: boolean;
}

interface BackendAdminUserUpsertRequest {
  name: string;
  email: string;
  role: AdminUserRole;
  password: string | null;
}

interface BackendAdminUserRoleResponse {
  code: string;
}
