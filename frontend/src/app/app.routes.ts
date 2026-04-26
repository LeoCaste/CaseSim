import { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell/app-shell';
import { VisualKitPage } from './features/visual-kit/pages/visual-kit-page/visual-kit-page';
import { InterviewPage } from './features/interview/pages/interview-page/interview-page';
import { SessionCompletedPage } from './features/interview/pages/session-completed-page/session-completed-page';
import { StudentDashboardPage } from './features/student/pages/student-dashboard-page/student-dashboard-page';
import { ProfessorDashboardPage } from './features/professor/pages/professor-dashboard-page/professor-dashboard-page';
import { ProfessorReviewPage } from './features/professor/pages/professor-review-page/professor-review-page';
import { WaitingRoomPage } from './features/student/pages/waiting-room-page/waiting-room-page';



export const routes: Routes = [
  {
    path: '',
    component: AppShell,
    children: [
      {
        path: 'visual-kit',
        component: VisualKitPage,
      },
      {
        path: 'interview',
        component: InterviewPage,
      },
      {
        path: 'student/dashboard',
        component: StudentDashboardPage,
      },
      {
        path: 'student/waiting-room',
        component: WaitingRoomPage,
      },
      {
        path: 'professor/review',
        component: ProfessorReviewPage,
      },
      {
        path: 'professor/dashboard',
        component: ProfessorDashboardPage,
      },
      {
        path: 'session-completed',
        component: SessionCompletedPage,
      },
      {
        path: '',
        redirectTo: 'student/dashboard',
        pathMatch: 'full',
      },
    ],
  },
];
