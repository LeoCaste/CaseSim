import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Mocked, vi } from 'vitest';

import { InterviewPage } from './interview-page';
import { UserContext } from '../../../../core/services/user-context';
import { InterviewSessionData, InterviewSessionService } from '../../../../core/services/interview-session.service';

describe('InterviewPage', () => {
  let component: InterviewPage;
  let fixture: ComponentFixture<InterviewPage>;
  let interviewSessionServiceSpy: Mocked<
    Pick<InterviewSessionService, 'getInterviewSession' | 'sendMessage' | 'submitFinalDiagnosis'>
  >;

  const sessionFixture: InterviewSessionData = {
    id: 'df9af08e-7dcc-43c7-a9f9-248f7d5de5d4',
    patientName: 'Paciente Simulado',
    age: 34,
    sex: 'F',
    context: 'Ambulatorio',
    reason: 'cefalea',
    messages: []
  };

  beforeEach(async () => {
    interviewSessionServiceSpy = {
      getInterviewSession: vi.fn(),
      sendMessage: vi.fn(),
      submitFinalDiagnosis: vi.fn()
    };
    interviewSessionServiceSpy.getInterviewSession.mockReturnValue(of(sessionFixture));
    interviewSessionServiceSpy.submitFinalDiagnosis.mockReturnValue(of(true));

    await TestBed.configureTestingModule({
      imports: [InterviewPage],
      providers: [
        {
          provide: InterviewSessionService,
          useValue: interviewSessionServiceSpy
        },
        {
          provide: UserContext,
          useValue: {
            setRole: vi.fn()
          }
        },
        {
          provide: Router,
          useValue: {
            navigate: vi.fn()
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InterviewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should append fallback messages when backend response is recovered by service', () => {
    interviewSessionServiceSpy.sendMessage.mockReturnValue(
      of([
        {
          id: 'm-student-01',
          role: 'STUDENT',
          content: '¿Desde cuándo presenta cefalea?',
          timestamp: '10:00'
        },
        {
          id: 'm-patient-fallback-01',
          role: 'PATIENT',
          content: 'Me cuesta responder con detalle ahora, pero el dolor empezó ayer.',
          timestamp: '10:00'
        }
      ])
    );
    component.clinicalIntervention = '¿Desde cuándo presenta cefalea?';

    component.sendIntervention();

    expect(component.loadError).toBe('');
    expect(component.isSendingMessage).toBe(false);
    expect(component.messages.length).toBe(2);
    expect(component.messages[1]?.content).toContain('Me cuesta responder');
  });

  it('should show temporary-unavailability message on 503 without usable body', () => {
    interviewSessionServiceSpy.sendMessage.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: null }))
    );
    component.clinicalIntervention = '¿Presenta otros síntomas asociados?';

    component.sendIntervention();

    expect(component.loadError).toBe('Paciente simulado temporalmente no disponible. Intenta nuevamente en unos minutos.');
    expect(component.isSendingMessage).toBe(false);
  });
});
