import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { StudentActivity } from '../../../../core/models/student-session.model';

@Component({
  selector: 'app-activity-card',
  imports: [CommonModule, RouterLink],
  templateUrl: './activity-card.html',
  styleUrl: './activity-card.css',
})
export class ActivityCard {
  @Input({ required: true }) activity!: StudentActivity;
  @Output() startRequested = new EventEmitter<string>();

  onStartRequested(): void {
    this.startRequested.emit(this.activity.id);
  }
}
