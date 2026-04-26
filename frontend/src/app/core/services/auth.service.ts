import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { AuthUser } from '../models/auth-user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUser: AuthUser | null = null;

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

    return of(this.currentUser);
  }

  me(): Observable<AuthUser | null> {
    return of(this.currentUser);
  }

  logout(): Observable<void> {
    this.currentUser = null;
    return of(void 0);
  }
}
