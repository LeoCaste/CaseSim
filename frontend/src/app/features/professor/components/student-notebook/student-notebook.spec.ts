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
    fixture.componentRef.setInput('notebook', {
      notes: 'Paciente refiere inicio súbito del dolor.',
      hypothesis: 'Síndrome coronario agudo en estudio.',
    });
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
