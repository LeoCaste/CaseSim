import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserContext } from '../../../../core/services/user-context';
import { RouterLink } from '@angular/router';
import { ProfessorCourseCard } from '../../components/professor-course-card/professor-course-card';
import { ProfessorActivitySummary } from '../../components/professor-activity-summary/professor-activity-summary';
import { RecentSessionTable } from '../../components/recent-session-table/recent-session-table';
import {
  ProfessorActivityOverview,
  ProfessorRecentSession,
  ProfessorSimulationOverview,
  ProfessorSummaryItem
} from '../../../../core/models/professor-dashboard.model';
import { ProfessorDashboardService } from '../../../../core/services/professor-dashboard.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-professor-dashboard-page',
  imports: [CommonModule, ProfessorCourseCard, ProfessorActivitySummary, RecentSessionTable, RouterLink,],
  templateUrl: './professor-dashboard-page.html',
  styleUrl: './professor-dashboard-page.css',
})
export class ProfessorDashboardPage implements OnInit {
  summary: ProfessorSummaryItem[] = [];
  simulations: ProfessorSimulationOverview[] = [];
  activities: ProfessorActivityOverview[] = [];
  sessions: ProfessorRecentSession[] = [];
  isLoading = false;
  loadError = '';

  constructor(
    private userContext: UserContext,
    private professorDashboardService: ProfessorDashboardService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';

    this.professorDashboardService
      .getDashboard()
      .pipe(
        finalize(() => {
          this.isLoading = false;
        })
      )
      .subscribe({
        next: (dashboard) => {
          this.summary = dashboard.summary;
          this.simulations = dashboard.simulations;
          this.activities = dashboard.activities;
          this.sessions = dashboard.sessions;
        },
        error: () => {
          this.summary = [];
          this.simulations = [];
          this.activities = [];
          this.sessions = [];
          this.loadError = 'No fue posible cargar el panel del profesor.';
        }
      });
  }
}
