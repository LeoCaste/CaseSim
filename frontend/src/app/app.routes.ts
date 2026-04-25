import { Routes } from '@angular/router';
import { AppShell } from './layout/app-shell/app-shell';
import { VisualKitPage } from './features/visual-kit/pages/visual-kit-page/visual-kit-page';

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
        path: '',
        redirectTo: 'visual-kit',
        pathMatch: 'full'
      }
    ]
  }
];
