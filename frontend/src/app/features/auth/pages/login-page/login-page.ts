import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../../../core/services/auth.service';
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
  isLoading = false;

  constructor(
    private router: Router,
    private userContext: UserContext,
    private authService: AuthService
  ) {}

  login(): void {
    this.error = '';
    this.isLoading = true;

    this.authService.login(this.email).subscribe((user) => {
      this.isLoading = false;

      if (!user) {
        this.error = 'Correo institucional no válido.';
        return;
      }

      this.userContext.setUser(user);
      this.router.navigate([user.role === 'professor' ? '/professor/dashboard' : '/student/dashboard']);
    });
  }
}
