import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfessorCourseCard } from './professor-course-card';

describe('ProfessorCourseCard', () => {
  let component: ProfessorCourseCard;
  let fixture: ComponentFixture<ProfessorCourseCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfessorCourseCard],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfessorCourseCard);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('simulation', {
      name: 'Simulación cardiología',
      caseName: 'Dolor torácico',
      course: 'Medicina Interna',
      students: 12,
      completedSessions: 4,
    });
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
