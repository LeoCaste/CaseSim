import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UserContext } from '../../core/services/user-context';

@Component({
  selector: 'app-topbar',
  imports: [],
  templateUrl: './topbar.html',
  styleUrl: './topbar.css'
})
export class Topbar {
  constructor(
    public userContext: UserContext,
    private authService: AuthService,
    private router: Router
  ) {}

  logout(): void {
    this.authService.logout().subscribe(() => {
      this.userContext.setUser(null);
      this.router.navigate(['/login']);
    });
  }
}
