import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './reset-password-page.html',
  styleUrl: './reset-password-page.css'
})
export class ResetPasswordPage {
  password = '';
  confirmPassword = '';
  isSubmitting = false;
  errorMessage = '';
  successMessage = '';

  private readonly token: string;

  constructor(
    route: ActivatedRoute,
    private authService: AuthService,
    private router: Router
  ) {
    this.token = route.snapshot.queryParamMap.get('token')?.trim() ?? '';
  }

  submit(): void {
    if (this.isSubmitting) {
      return;
    }

    this.errorMessage = this.validateForm();
    this.successMessage = '';
    if (this.errorMessage) {
      return;
    }

    this.isSubmitting = true;

    this.authService
      .resetPassword({
        token: this.token,
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
          this.successMessage = 'Tu contraseña fue restablecida. Ahora puedes iniciar sesión.';
          setTimeout(() => {
            void this.router.navigate(['/login']);
          }, 700);
        },
        error: () => {
          this.errorMessage = 'No fue posible restablecer la contraseña. Verifica el enlace o solicita uno nuevo.';
        }
      });
  }

  private validateForm(): string {
    if (!this.token) {
      return 'El enlace de recuperación no es válido.';
    }

    if (!/^(?=.*[A-Z])(?=.*\d).{8,}$/.test(this.password)) {
      return 'La contraseña debe tener al menos 8 caracteres, una mayúscula y un número.';
    }

    if (this.password !== this.confirmPassword) {
      return 'La confirmación de contraseña no coincide.';
    }

    return '';
  }
}
