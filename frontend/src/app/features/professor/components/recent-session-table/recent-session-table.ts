import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface RecentSession {
  student: string;
  activity: string;
  status: string;
  turns: number;
  duration: string;
}

@Component({
  selector: 'app-recent-session-table',
  imports: [CommonModule],
  templateUrl: './recent-session-table.html',
  styleUrl: './recent-session-table.css',
})
export class RecentSessionTable {
  @Input({ required: true }) sessions: RecentSession[] = [];
}
