import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-setup-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './setup-page.html',
  styleUrl: './setup-page.css'
})
export class SetupPage {
  email = '';
  password = '';
  confirmPassword = '';

  isSubmitting = false;
  errorMessage = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  submit(): void {
    if (this.isSubmitting) {
      return;
    }

    this.errorMessage = this.validateForm();
    if (this.errorMessage) {
      return;
    }

    this.isSubmitting = true;

    this.authService
      .bootstrapAdmin({
        email: this.normalizeEmail(this.email),
        password: this.password,
        confirmPassword: this.confirmPassword
      })
      .pipe(
        finalize(() => {
          this.isSubmitting = false;
        })
      )
      .subscribe({
        next: () => {
          void this.router.navigate(['/login']);
        },
        error: (error) => {
          this.errorMessage = this.mapBootstrapError(error);
        }
      });
  }

  private validateForm(): string {
    if (!this.email.trim() || !this.password.trim() || !this.confirmPassword.trim()) {
      return 'Todos los campos son obligatorios.';
    }

    const email = this.normalizeEmail(this.email);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return 'Debes ingresar un correo válido.';
    }

    if (!this.isPasswordValid(this.password)) {
      return 'La contraseña debe tener al menos 8 caracteres, una mayúscula y un número.';
    }

    if (this.password !== this.confirmPassword) {
      return 'La confirmación de contraseña no coincide.';
    }

    return '';
  }

  private normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
  }

  private mapBootstrapError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const backendMessage = this.extractBackendMessage(error);
      if (backendMessage) {
        return backendMessage;
      }

      if (error.status === 0) {
        return 'No se pudo conectar con el servidor. Intenta nuevamente.';
      }
    }

    return 'No fue posible crear el primer administrador. Revisa los datos e intenta nuevamente.';
  }

  private extractBackendMessage(error: HttpErrorResponse): string {
    if (error.error && typeof error.error === 'object' && 'message' in error.error) {
      const message = (error.error as { message?: unknown }).message;
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }
    }

    return '';
  }

  private isPasswordValid(password: string): boolean {
    return /^(?=.*[A-Z])(?=.*\d).{8,}$/.test(password);
  }
}
