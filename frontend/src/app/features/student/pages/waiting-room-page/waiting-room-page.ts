import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { StudentSessionService } from '../../../../core/services/student-session.service';
import { StudentActivity } from '../../../../core/models/student-session.model';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-waiting-room-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './waiting-room-page.html',
  styleUrl: './waiting-room-page.css',
})
export class WaitingRoomPage implements OnInit {
  activity: StudentActivity | null = null;
  isLoading = false;
  loadError = '';
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private userContext: UserContext,
    private studentSessionService: StudentSessionService,
    private cdr: ChangeDetectorRef
  ) {
    this.userContext.setRole('student');
  }

  ngOnInit(): void {
    const currentActivityId = this.studentSessionService.getCurrentActivityId();

    if (!currentActivityId) {
      this.loadError = 'No se encontró una actividad seleccionada. Vuelve al panel y selecciona una entrevista.';
      return;
    }

    this.isLoading = true;
    this.loadError = '';

    this.studentSessionService
      .getActivities()
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (activities) => {
          this.activity = activities.find((item) => item.id === currentActivityId) ?? null;
          if (!this.activity) {
            this.loadError = 'La actividad seleccionada no está disponible. Vuelve al panel para recargarla.';
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.activity = null;
          this.loadError = 'No fue posible cargar la sala de espera. Intenta nuevamente.';
          this.cdr.detectChanges();
        }
      });
  }

  get waitingTitle(): string {
    return this.activity?.title?.trim() || 'Entrevista clínica';
  }

  get patientDisplayName(): string {
    return this.activity?.patient?.trim() || 'Paciente simulado';
  }

  get patientInitials(): string {
    const normalized = this.patientDisplayName
      .split(' ')
      .map((part) => part.trim())
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');

    return normalized || 'PS';
  }
}
