import { AfterViewInit, Component, ElementRef, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TranscriptMessage {
  role: 'Paciente' | 'Estudiante' | 'Sistema' | 'PATIENT' | 'STUDENT' | 'SYSTEM';
  time: string;
  content: string;
}

@Component({
  selector: 'app-session-transcript',
  imports: [CommonModule],
  templateUrl: './session-transcript.html',
  styleUrl: './session-transcript.css',
})
export class SessionTranscript implements AfterViewInit, OnChanges {
  @ViewChild('transcriptList') private transcriptList?: ElementRef<HTMLElement>;

  @Input({ required: true }) messages: TranscriptMessage[] = [];

  isPatientMessage(role: TranscriptMessage['role']): boolean {
    const normalizedRole = role.trim().toLowerCase();
    return normalizedRole === 'paciente' || normalizedRole === 'patient';
  }

  isStudentMessage(role: TranscriptMessage['role']): boolean {
    const normalizedRole = role.trim().toLowerCase();
    return normalizedRole === 'estudiante' || normalizedRole === 'student';
  }

  ngAfterViewInit(): void {
    this.scrollToBottom();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['messages']) {
      return;
    }

    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    requestAnimationFrame(() => {
      const list = this.transcriptList?.nativeElement;

      if (!list) {
        return;
      }

      list.scrollTop = list.scrollHeight;
    });
  }
}
