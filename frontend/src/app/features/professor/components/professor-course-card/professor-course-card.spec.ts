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
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
