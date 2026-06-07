import { Component } from '@angular/core';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { UserContext } from '../../core/services/user-context';

@Component({
  selector: 'app-topbar',
  imports: [],
  templateUrl: './topbar.html',
  styleUrl: './topbar.css'
})
export class Topbar {
  isLoggingOut = false;

  constructor(
    public userContext: UserContext,
    private authService: AuthService
  ) {}

  logout(): void {
    if (this.isLoggingOut) {
      return;
    }

    const confirmed = window.confirm('¿Estás seguro de que deseas cerrar sesión?');
    if (!confirmed) {
      return;
    }

    this.isLoggingOut = true;
    this.authService
      .logout()
      .pipe(
        finalize(() => {
          this.isLoggingOut = false;
        })
      )
      .subscribe(() => {
        this.userContext.setUser(null);
      });
  }
}
