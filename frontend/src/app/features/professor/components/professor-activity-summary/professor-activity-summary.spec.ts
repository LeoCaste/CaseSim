import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfessorActivitySummary } from './professor-activity-summary';

describe('ProfessorActivitySummary', () => {
  let component: ProfessorActivitySummary;
  let fixture: ComponentFixture<ProfessorActivitySummary>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfessorActivitySummary],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfessorActivitySummary);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activity', {
      title: 'Entrevista clínica inicial',
      course: 'Medicina Interna',
      caseName: 'Dolor torácico',
      status: 'En progreso',
      completed: 2,
      total: 5,
    });
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
