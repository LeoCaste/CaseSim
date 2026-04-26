import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentSessionDetailPage } from './student-session-detail-page';

describe('StudentSessionDetailPage', () => {
  let component: StudentSessionDetailPage;
  let fixture: ComponentFixture<StudentSessionDetailPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentSessionDetailPage],
    }).compileComponents();

    fixture = TestBed.createComponent(StudentSessionDetailPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
