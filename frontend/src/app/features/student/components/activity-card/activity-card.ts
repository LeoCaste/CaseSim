import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

export interface StudentActivity {
  title: string;
  course: string;
  professor: string;
  patient: string;
  status: string;
  statusType: string;
  duration: string;
  description: string;
  actionLabel: string;
  route: string | null;
}

@Component({
  selector: 'app-activity-card',
  imports: [CommonModule, RouterLink],
  templateUrl: './activity-card.html',
  styleUrl: './activity-card.css'
})
export class ActivityCard {
  @Input({ required: true }) activity!: StudentActivity;
}
