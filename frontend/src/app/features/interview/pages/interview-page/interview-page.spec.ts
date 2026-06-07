import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
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

  it('should add student message immediately when sending', () => {
    interviewSessionServiceSpy.sendMessage.mockReturnValue(
      of([
        {
          id: 'm-student-01',
          role: 'STUDENT',
          content: '¿Desde cuándo presenta cefalea?',
          timestamp: '10:00'
        },
        {
          id: 'm-patient-01',
          role: 'PATIENT',
          content: 'Me duele desde ayer.',
          timestamp: '10:01'
        }
      ])
    );
    component.clinicalIntervention = '¿Desde cuándo presenta cefalea?';

    component.sendIntervention();

    // Student message should be at index 0
    expect(component.messages.length).toBe(2);
    expect(component.messages[0].role).toBe('Estudiante');
    expect(component.messages[0].content).toBe('¿Desde cuándo presenta cefalea?');
    // Patient response should be at index 1
    expect(component.messages[1].role).toBe('Paciente');
    expect(component.messages[1].content).toContain('Me duele desde ayer');
  });

  it('should scroll when sending message', () => {
    const scrollSpy = vi.spyOn(component as unknown as { scrollConversationToBottom: () => void }, 'scrollConversationToBottom');
    interviewSessionServiceSpy.sendMessage.mockReturnValue(
      of([
        {
          id: 'm-student-01',
          role: 'STUDENT',
          content: 'Test',
          timestamp: '10:00'
        },
        {
          id: 'm-patient-01',
          role: 'PATIENT',
          content: 'Response',
          timestamp: '10:01'
        }
      ])
    );
    component.clinicalIntervention = 'Test';

    component.sendIntervention();

    expect(scrollSpy).toHaveBeenCalled();
  });

  it('should keep student message visible on error', () => {
    interviewSessionServiceSpy.sendMessage.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: 'Server error' }))
    );
    component.clinicalIntervention = '¿Presenta otros síntomas?';

    component.sendIntervention();

    // Student message should remain visible even on error
    expect(component.messages.length).toBe(1);
    expect(component.messages[0].role).toBe('Estudiante');
    expect(component.messages[0].content).toBe('¿Presenta otros síntomas?');
    expect(component.loadError).toBeTruthy();
    expect(component.isSendingMessage).toBe(false);
  });

  it('should keep student message visible during loading', () => {
    // Return an observable that never emits to simulate ongoing request
    interviewSessionServiceSpy.sendMessage.mockReturnValue(
      new Observable<never>(() => {
        // Never emit - stay in loading state
      })
    );
    component.clinicalIntervention = 'Mensaje durante carga';

    component.sendIntervention();

    // Student message should be present while service is processing
    expect(component.messages.length).toBe(1);
    expect(component.messages[0].role).toBe('Estudiante');
    expect(component.messages[0].content).toBe('Mensaje durante carga');
    expect(component.isSendingMessage).toBe(true);
  });
});
