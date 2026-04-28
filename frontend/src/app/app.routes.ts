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
import { AdminLlmConfigPage } from './features/admin/pages/admin-llm-config-page/admin-llm-config-page';
import { AdminLlmUsagePage } from './features/admin/pages/admin-llm-usage-page/admin-llm-usage-page';
import { AdminUsersPage } from './features/admin/pages/admin-users-page/admin-users-page';
import { rootSessionRedirectGuard } from './core/guards/root-session-redirect.guard';
import { roleAuthorizationCanActivate, roleAuthorizationCanMatch } from './core/guards/role-authorization.guard';

const PROFESSOR_ONLY = { roles: ['professor'] };
const STUDENT_ONLY = { roles: ['student'] };
const ADMIN_ONLY = { roles: ['admin'] };

export const routes: Routes = [
  {
    path: 'login',
    component: LoginPage
  },
  {
    path: '',
    pathMatch: 'full',
    canActivate: [rootSessionRedirectGuard],
    component: LoginPage
  },
  {
    path: '',
    component: AppShell,
    children: [
      {
        path: 'interview',
        component: InterviewPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: STUDENT_ONLY
      },
      {
        path: 'student/dashboard',
        component: StudentDashboardPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: STUDENT_ONLY
      },
      {
        path: 'student/waiting-room',
        component: WaitingRoomPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: STUDENT_ONLY
      },
      {
        path: 'student/session-detail',
        component: StudentSessionDetailPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: STUDENT_ONLY
      },
      {
        path: 'clinical-cases',
        component: ClinicalCaseListPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'professor/clinical-cases',
        component: ClinicalCaseListPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'clinical-cases/new',
        component: ClinicalCaseFormPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'clinical-cases/:id/assign',
        component: AssignSimulationPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'clinical-cases/:id/edit',
        component: ClinicalCaseFormPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'clinical-cases/:id',
        component: ClinicalCaseDetailPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'professor/review',
        component: ProfessorReviewPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'professor/dashboard',
        component: ProfessorDashboardPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: PROFESSOR_ONLY
      },
      {
        path: 'session-completed',
        component: SessionCompletedPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: STUDENT_ONLY
      },
      {
        path: 'admin/llm-config',
        component: AdminLlmConfigPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: ADMIN_ONLY
      },
      {
        path: 'admin/llm-usage',
        component: AdminLlmUsagePage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: ADMIN_ONLY
      },
      {
        path: 'admin/users',
        component: AdminUsersPage,
        canActivate: [roleAuthorizationCanActivate],
        canMatch: [roleAuthorizationCanMatch],
        data: ADMIN_ONLY
      }
    ]
  }
];
