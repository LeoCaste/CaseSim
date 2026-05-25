import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProfessorRecentSession as RecentSession } from '../../../../core/models/professor-dashboard.model';

@Component({
  selector: 'app-recent-session-table',
  imports: [CommonModule, RouterLink],
  templateUrl: './recent-session-table.html',
  styleUrl: './recent-session-table.css'
})
export class RecentSessionTable {
  @Input({ required: true }) sessions: RecentSession[] = [];
}
