import { CommonModule } from '@angular/common';
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
  institutionName = '';
  adminName = '';
  adminEmail = '';
  password = '';
  confirmPassword = '';
  bootstrapToken = '';

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
        institutionName: this.institutionName.trim(),
        adminName: this.adminName.trim(),
        adminEmail: this.adminEmail.trim().toLowerCase(),
        password: this.password,
        confirmPassword: this.confirmPassword,
        bootstrapToken: this.bootstrapToken.trim()
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
        error: () => {
          this.errorMessage = 'No fue posible completar la configuración inicial. Verifica los datos e intenta nuevamente.';
        }
      });
  }

  private validateForm(): string {
    if (!this.institutionName.trim() || !this.adminName.trim() || !this.adminEmail.trim() || !this.bootstrapToken.trim()) {
      return 'Todos los campos son obligatorios.';
    }

    const email = this.adminEmail.trim().toLowerCase();
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

  private isPasswordValid(password: string): boolean {
    return /^(?=.*[A-Z])(?=.*\d).{8,}$/.test(password);
  }
}
