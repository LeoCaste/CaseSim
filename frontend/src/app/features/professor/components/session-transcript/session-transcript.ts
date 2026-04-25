import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TranscriptMessage {
  role: string;
  time: string;
  content: string;
}

@Component({
  selector: 'app-session-transcript',
  imports: [CommonModule],
  templateUrl: './session-transcript.html',
  styleUrl: './session-transcript.css',
})
export class SessionTranscript {
  @Input({ required: true }) messages: TranscriptMessage[] = [];
}
