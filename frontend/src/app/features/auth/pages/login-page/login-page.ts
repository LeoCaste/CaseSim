import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, TimeoutError, timeout } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-login-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css'
})
export class LoginPage {
  email = '';
  password = '';
  requiresPassword = false;
  showForgotPasswordLink = false;
  private passwordRequiredForEmail: string | null = null;

  errorMessage = '';
  isCheckingEmail = false;
  isLoggingIn = false;

  constructor(
    private router: Router,
    private userContext: UserContext,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  onEmailChange(nextEmail: string): void {
    const normalizedEmail = this.normalizeEmail(nextEmail);

    if (this.passwordRequiredForEmail === normalizedEmail) {
      this.errorMessage = '';
      return;
    }

    this.requiresPassword = false;
    this.password = '';
    this.passwordRequiredForEmail = null;
    this.errorMessage = '';
    this.showForgotPasswordLink = false;
  }

  login(): void {
    if (this.isCheckingEmail || this.isLoggingIn) {
      return;
    }

    const normalizedEmail = this.normalizeEmail(this.email);

    if (!normalizedEmail) {
      this.errorMessage = 'El correo institucional es obligatorio.';
      return;
    }

    const hasPassword = this.password.trim().length > 0;

    if (this.requiresPassword || hasPassword) {
      if (!hasPassword) {
        this.errorMessage = 'La contraseña es obligatoria para este usuario.';
        return;
      }

      this.submitLogin(normalizedEmail);
      return;
    }

    this.performPreCheckAndContinue(normalizedEmail);
  }

  private performPreCheckAndContinue(email: string): void {
    if (this.isCheckingEmail) {
      return;
    }

    this.errorMessage = '';
    this.isCheckingEmail = true;
    this.isLoggingIn = false;

    this.authService
      .preCheck(email)
      .pipe(timeout(5000))
      .subscribe({
        next: (response) => {
          this.requiresPassword = response.requiresPassword === true;
          this.passwordRequiredForEmail = this.requiresPassword ? email : null;
          this.showForgotPasswordLink = false;
          this.isCheckingEmail = false;
          this.isLoggingIn = false;

          if (this.requiresPassword) {
            this.cdr.detectChanges();
            return;
          }

          this.submitLogin(email);
        },
        error: (error) => {
          this.isCheckingEmail = false;
          this.isLoggingIn = false;
          this.requiresPassword = false;
          this.passwordRequiredForEmail = null;
          this.errorMessage = this.mapPreCheckError(error);
        }
      });
  }

  private submitLogin(email: string): void {
    this.errorMessage = '';
    this.showForgotPasswordLink = false;
    this.isCheckingEmail = false;
    this.isLoggingIn = true;

    this.authService
      .login({
        email,
        ...(this.password.trim().length > 0 ? { password: this.password } : {})
      })
      .pipe(
        finalize(() => {
          this.isCheckingEmail = false;
          this.isLoggingIn = false;
        })
      )
      .subscribe({
        next: (user) => {
          this.userContext.setUser(user);
          this.router.navigate([this.resolveDashboardRoute(user.role)]);
        },
        error: (error) => {
          this.showForgotPasswordLink = this.isInvalidAdminPasswordError(error, email);
          this.errorMessage = this.mapLoginError(error);
        }
      });
  }

  get submitButtonLabel(): string {
    if (this.isCheckingEmail) {
      return 'Validando correo...';
    }

    if (this.isLoggingIn) {
      return 'Ingresando...';
    }

    return 'Ingresar';
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
    if (error instanceof TimeoutError) {
      return 'No se pudo validar el correo. Intenta nuevamente.';
    }

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

  private isInvalidAdminPasswordError(error: unknown, attemptedEmail: string): boolean {
    if (!this.requiresPassword || this.passwordRequiredForEmail !== attemptedEmail) {
      return false;
    }

    if (error instanceof HttpErrorResponse && error.status === 401) {
      return true;
    }

    return error instanceof Error && error.message === 'UNAUTHORIZED';
  }
}
