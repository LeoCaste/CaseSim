export type AdminUserRole = string;

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: AdminUserRole;
  active: boolean;
}

export interface AdminUserCreatePayload {
  name: string;
  email: string;
  role: AdminUserRole;
  password?: string;
}

export interface AdminUserUpdatePayload {
  name: string;
  email: string;
  role: AdminUserRole;
  password?: string;
}

export interface AdminUserApiError {
  status: number;
  code: 'VALIDATION' | 'UNAUTHORIZED' | 'FORBIDDEN' | 'CONFLICT' | 'UNKNOWN';
  message: string;
}

export interface AdminUserRoleOption {
  code: string;
}

export function getAdminUserRoleLabel(role: string): string {
  switch (role) {
    case 'ADMIN':
      return 'Administrador';
    case 'PROFESOR':
      return 'Profesor';
    case 'ESTUDIANTE':
      return 'Estudiante';
    default:
      return role;
  }
}
