import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { ActivityCard } from '../../components/activity-card/activity-card';
import { RouterLink } from '@angular/router';
import { StudentActivity } from '../../../../core/models/student-session.model';
import { StudentSessionService } from '../../../../core/services/student-session.service';

@Component({
  selector: 'app-student-dashboard-page',
  imports: [CommonModule, ActivityCard, RouterLink],
  templateUrl: './student-dashboard-page.html',
  styleUrl: './student-dashboard-page.css'
})
export class StudentDashboardPage implements OnInit {
  activities: StudentActivity[] = [];
  history: Array<{ title: string; patient: string; status: string; date: string; route: string }> = [];
  isLoading = false;
  loadError = '';

  constructor(
    private userContext: UserContext,
    private studentSessionService: StudentSessionService
  ) {
    this.userContext.setRole('student');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';

    this.studentSessionService.getDashboardData().subscribe({
      next: (data) => {
        this.activities = data.activities;
        this.history = data.history;
        this.isLoading = false;
      },
      error: () => {
        this.activities = [];
        this.history = [];
        this.loadError = 'No fue posible cargar las actividades. Intenta nuevamente.';
        this.isLoading = false;
      }
    });
  }

  onStartActivity(activityId: string): void {
    this.studentSessionService.setCurrentActivityId(activityId);
  }
}
