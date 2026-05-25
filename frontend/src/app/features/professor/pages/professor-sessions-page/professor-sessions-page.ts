import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ProfessorDashboardService } from '../../../../core/services/professor-dashboard.service';
import { ProfessorRecentSession } from '../../../../core/models/professor-dashboard.model';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-professor-sessions-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './professor-sessions-page.html',
  styleUrl: './professor-sessions-page.css'
})
export class ProfessorSessionsPage implements OnInit {
  sessions: ProfessorRecentSession[] = [];
  isLoading = false;
  loadError = '';
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private professorDashboardService: ProfessorDashboardService,
    private userContext: UserContext,
    private cdr: ChangeDetectorRef
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.loadError = '';
    this.sessions = [];
    this.isLoading = true;
    this.professorDashboardService
      .getDashboard()
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (dashboard) => {
          this.sessions = dashboard.sessions;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadError = 'No fue posible cargar las sesiones.';
          this.sessions = [];
          this.cdr.detectChanges();
        }
      });
  }
}
