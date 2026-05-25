import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentNotebook } from './student-notebook';

describe('StudentNotebook', () => {
  let component: StudentNotebook;
  let fixture: ComponentFixture<StudentNotebook>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentNotebook],
    }).compileComponents();

    fixture = TestBed.createComponent(StudentNotebook);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
