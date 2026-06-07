import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ActivityCard } from './activity-card';

describe('ActivityCard', () => {
  let component: ActivityCard;
  let fixture: ComponentFixture<ActivityCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityCard],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(ActivityCard);
    component = fixture.componentInstance;
    component.activity = {
      id: 'activity-1',
      title: 'Entrevista clínica',
      course: 'Medicina interna',
      professor: 'Prof. Rivera',
      patient: 'Paciente simulado',
      status: 'Pendiente',
      statusType: 'neutral',
      duration: '30 min',
      description: 'Actividad de prueba',
      actionLabel: 'Iniciar',
      route: '/student/waiting-room'
    };
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not render activity duration for students', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Duración');
    expect(fixture.nativeElement.textContent).not.toContain('30 min');
  });
});
