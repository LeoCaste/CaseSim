import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-login-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css'
})
export class LoginPage {
  email = '';
  error = '';

  constructor(
    private router: Router,
    private userContext: UserContext
  ) {}

  login(): void {
    const email = this.email.trim().toLowerCase();

    const isProfessor = /^[a-z]+\.[a-z]+@ufrontera\.cl$/.test(email);
    const isStudent = /^[a-z]\.[a-z]+\d*@ufromail\.cl$/.test(email);

    if (isProfessor) {
      this.userContext.setRole('professor');
      this.router.navigate(['/professor/dashboard']);
      return;
    }

    if (isStudent) {
      this.userContext.setRole('student');
      this.router.navigate(['/student/dashboard']);
      return;
    }

    this.error = 'Correo institucional no válido.';
  }
}
