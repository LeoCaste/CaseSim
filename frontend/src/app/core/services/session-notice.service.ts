import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SessionNoticeService {
  private readonly messageSubject = new BehaviorSubject<string>('');
  readonly message$ = this.messageSubject.asObservable();

  setMessage(message: string): void {
    this.messageSubject.next(message);
  }

  clearMessage(): void {
    this.messageSubject.next('');
  }
}
