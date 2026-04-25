import { Routes } from '@angular/router';
import { VisualKitPage } from './features/visual-kit/pages/visual-kit-page/visual-kit-page';

export const routes: Routes = [
  {
    path: 'visual-kit',
    component: VisualKitPage
  },
  {
    path: '',
    redirectTo: 'visual-kit',
    pathMatch: 'full'
  }
];
