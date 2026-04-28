import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
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
  password = '';
  requiresPassword = false;
  private preCheckedEmail: string | null = null;

  error = '';
  isLoading = false;
  isPreChecking = false;

  constructor(
    private router: Router,
    private userContext: UserContext,
    private authService: AuthService
  ) {}

  onEmailChange(): void {
    this.requiresPassword = false;
    this.password = '';
    this.preCheckedEmail = null;
    this.error = '';
  }

  login(): void {
    const normalizedEmail = this.normalizeEmail(this.email);

    if (!normalizedEmail) {
      this.error = 'El correo institucional es obligatorio.';
      return;
    }

    if (this.preCheckedEmail !== normalizedEmail) {
      this.performPreCheckAndContinue(normalizedEmail);
      return;
    }

    if (this.requiresPassword && !this.password.trim()) {
      this.error = 'La contraseña es obligatoria para este usuario.';
      return;
    }

    this.submitLogin(normalizedEmail);
  }

  private performPreCheckAndContinue(email: string): void {
    this.error = '';
    this.isLoading = true;
    this.isPreChecking = true;

    this.authService.preCheck(email).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.isPreChecking = false;
        this.preCheckedEmail = email;
        this.requiresPassword = response.requiresPassword;

        if (response.requiresPassword) {
          return;
        }

        this.submitLogin(email);
      },
      error: (error) => {
        this.isLoading = false;
        this.isPreChecking = false;
        this.preCheckedEmail = email;
        this.requiresPassword = true;
        this.error = this.mapPreCheckError(error);
      }
    });
  }

  private submitLogin(email: string): void {
    this.error = '';
    this.isLoading = true;

    this.authService
      .login({
        email,
        ...(this.requiresPassword ? { password: this.password.trim() } : {})
      })
      .subscribe({
        next: (user) => {
          this.isLoading = false;
          this.userContext.setUser(user);
          this.router.navigate([this.resolveDashboardRoute(user.role)]);
        },
        error: (error) => {
          this.isLoading = false;
          this.error = this.mapLoginError(error);
        }
      });
  }

  get submitButtonLabel(): string {
    if (!this.isLoading) {
      return 'Ingresar';
    }

    return this.isPreChecking ? 'Validando correo...' : 'Ingresando...';
  }

  private resolveDashboardRoute(role: 'student' | 'professor' | 'admin'): string {
    if (role === 'admin') {
      return '/admin/llm-config';
    }

    if (role === 'professor') {
      return '/professor/dashboard';
    }

    return '/student/dashboard';
  }

  private mapPreCheckError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'No fue posible validar el correo con el servidor. Si eres administrador, ingresa tu contraseña e intenta nuevamente.';
    }

    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'No fue posible validar el correo en este momento. Si eres administrador, ingresa tu contraseña e intenta nuevamente.';
    }

    if (error instanceof Error && error.message === 'UNAUTHORIZED') {
      return 'No fue posible validar el correo en este momento. Si eres administrador, ingresa tu contraseña e intenta nuevamente.';
    }

    return 'No fue posible validar el correo. Si eres administrador, ingresa tu contraseña e intenta nuevamente.';
  }

  private normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
  }

  private mapLoginError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return this.requiresPassword ? 'Contraseña inválida.' : 'Credenciales inválidas.';
    }

    if (error instanceof Error && error.message === 'UNAUTHORIZED') {
      return this.requiresPassword ? 'Contraseña inválida.' : 'Credenciales inválidas.';
    }

    return 'No fue posible iniciar sesión. Intenta nuevamente.';
  }
}
