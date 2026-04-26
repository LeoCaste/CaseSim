import { Injectable } from '@angular/core';
import { AuthUser, UserRole } from '../models/auth-user.model';

@Injectable({
  providedIn: 'root'
})
export class UserContext {
  private role: UserRole = 'student';
  private authUser: AuthUser | null = null;

  setRole(role: UserRole): void {
    this.role = role;
  }

  getRole(): UserRole {
    return this.role;
  }

  setUser(user: AuthUser | null): void {
    this.authUser = user;
    if (user) {
      this.role = user.role;
    }
  }

  getUser(): AuthUser | null {
    return this.authUser;
  }

  getRoleLabel(): string {
    return this.role === 'student' ? 'Estudiante' : 'Profesor';
  }
}
