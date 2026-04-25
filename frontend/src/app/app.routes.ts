import { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell/app-shell';
import { VisualKitPage } from './features/visual-kit/pages/visual-kit-page/visual-kit-page';
import { InterviewPage } from './features/interview/pages/interview-page/interview-page';
import { SessionCompletedPage } from './features/interview/pages/session-completed-page/session-completed-page';
import { StudentDashboardPage } from './features/student/pages/student-dashboard-page/student-dashboard-page';

export const routes: Routes = [
  {
    path: '',
    component: AppShell,
    children: [
      {
        path: 'visual-kit',
        component: VisualKitPage
      },
      {
        path: 'interview',
        component: InterviewPage
      },
      {
        path: 'student/dashboard',
        component: StudentDashboardPage
      },
      {
        path: 'session-completed',
        component: SessionCompletedPage
      },
      {
        path: '',
        redirectTo: 'student/dashboard',
        pathMatch: 'full'
      }
    ]
  }
];
