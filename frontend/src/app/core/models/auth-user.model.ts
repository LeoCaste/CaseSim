export type UserRole = 'student' | 'professor';

export interface AuthUser {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
}
