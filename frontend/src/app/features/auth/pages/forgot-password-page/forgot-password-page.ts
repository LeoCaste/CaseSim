import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './forgot-password-page.html',
  styleUrl: './forgot-password-page.css'
})
export class ForgotPasswordPage {
  email = '';
  isSubmitting = false;
  errorMessage = '';
  infoMessage = '';

  constructor(
    private authService: AuthService,
    private route: ActivatedRoute
  ) {
    const emailFromQueryParam = this.route.snapshot.queryParamMap.get('email');
    if (emailFromQueryParam) {
      this.email = emailFromQueryParam.trim().toLowerCase();
    }
  }

  submit(): void {
    if (this.isSubmitting) {
      return;
    }

    if (!this.email.trim()) {
      this.errorMessage = 'El correo es obligatorio.';
      this.infoMessage = '';
      return;
    }

    this.errorMessage = '';
    this.infoMessage = '';
    this.isSubmitting = true;

    this.authService
      .forgotPassword({ email: this.email })
      .pipe(
        finalize(() => {
          this.isSubmitting = false;
        })
      )
      .subscribe({
        next: () => {
          this.infoMessage =
            'Si el correo existe en la plataforma, recibirás instrucciones para restablecer tu contraseña.';
        },
        error: () => {
          this.errorMessage = 'No fue posible procesar la solicitud. Intenta nuevamente.';
        }
      });
  }
}
