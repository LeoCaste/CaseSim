import { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell/app-shell';
import { LoginPage } from './features/auth/pages/login-page/login-page';
import { InterviewPage } from './features/interview/pages/interview-page/interview-page';
import { SessionCompletedPage } from './features/interview/pages/session-completed-page/session-completed-page';
import { StudentDashboardPage } from './features/student/pages/student-dashboard-page/student-dashboard-page';
import { WaitingRoomPage } from './features/student/pages/waiting-room-page/waiting-room-page';
import { ProfessorDashboardPage } from './features/professor/pages/professor-dashboard-page/professor-dashboard-page';
import { ProfessorReviewPage } from './features/professor/pages/professor-review-page/professor-review-page';
import { ClinicalCaseListPage } from './features/clinical-cases/pages/clinical-case-list-page/clinical-case-list-page';
import { ClinicalCaseFormPage } from './features/clinical-cases/pages/clinical-case-form-page/clinical-case-form-page';
import { ClinicalCaseDetailPage } from './features/clinical-cases/pages/clinical-case-detail-page/clinical-case-detail-page';
import { AssignSimulationPage } from './features/clinical-cases/pages/assign-simulation-page/assign-simulation-page';
import { StudentSessionDetailPage } from './features/student/pages/student-session-detail-page/student-session-detail-page';


export const routes: Routes = [
  {
    path: 'login',
    component: LoginPage
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'login'
  },
  {
    path: '',
    component: AppShell,
    children: [
      {
        path: 'interview',
        component: InterviewPage
      },
      {
        path: 'student/dashboard',
        component: StudentDashboardPage
      },
      {
        path: 'student/waiting-room',
        component: WaitingRoomPage
      },
      {
        path: 'student/session-detail',
        component: StudentSessionDetailPage
      },
      {
        path: 'clinical-cases',
        component: ClinicalCaseListPage
      },
      {
        path: 'clinical-cases/new',
        component: ClinicalCaseFormPage
      },
      {
        path: 'clinical-cases/:id/assign',
        component: AssignSimulationPage
      },
      {
        path: 'clinical-cases/:id/edit',
        component: ClinicalCaseFormPage
      },
      {
        path: 'clinical-cases/:id',
        component: ClinicalCaseDetailPage
      },
      {
        path: 'professor/review',
        component: ProfessorReviewPage
      },
      {
        path: 'professor/dashboard',
        component: ProfessorDashboardPage
      },
      {
        path: 'session-completed',
        component: SessionCompletedPage
      }
    ]
  }
];
