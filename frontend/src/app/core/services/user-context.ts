import { Injectable } from '@angular/core';

export type UserRole = 'student' | 'professor';

@Injectable({
  providedIn: 'root'
})
export class UserContext {
  private role: UserRole = 'student';

  setRole(role: UserRole): void {
    this.role = role;
  }

  getRole(): UserRole {
    return this.role;
  }

  getRoleLabel(): string {
    return this.role === 'student' ? 'Estudiante' : 'Profesor';
  }
}
