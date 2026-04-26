import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { AuthUser } from '../models/auth-user.model';

const AUTH_STORAGE_KEY = 'casesim.auth.user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUser: AuthUser | null = null;

  constructor() {
    this.currentUser = this.loadStoredUser();
  }

  login(email: string, _password = ''): Observable<AuthUser | null> {
    const normalizedEmail = email.trim().toLowerCase();
    const professorMatch = /^[a-z]+\.[a-z]+@ufrontera\.cl$/.test(normalizedEmail);
    const studentMatch = /^[a-z]\.[a-z]+\d*@ufromail\.cl$/.test(normalizedEmail);

    if (!professorMatch && !studentMatch) {
      return of(null);
    }

    this.currentUser = {
      id: professorMatch ? 'prof-01' : 'stud-01',
      fullName: professorMatch ? 'Docente CaseSim' : 'Estudiante CaseSim',
      email: normalizedEmail,
      role: professorMatch ? 'professor' : 'student'
    };

    this.persistUser(this.currentUser);

    return of(this.currentUser);
  }

  me(): Observable<AuthUser | null> {
    return of(this.getCurrentUser());
  }

  getCurrentUser(): AuthUser | null {
    if (!this.currentUser) {
      this.currentUser = this.loadStoredUser();
    }

    return this.currentUser;
  }

  logout(): Observable<void> {
    this.currentUser = null;
    this.clearStoredUser();
    return of(void 0);
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

      if (parsed.role !== 'student' && parsed.role !== 'professor') {
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
}
