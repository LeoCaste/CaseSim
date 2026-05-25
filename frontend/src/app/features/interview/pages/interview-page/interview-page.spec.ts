import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InterviewPage } from './interview-page';

describe('InterviewPage', () => {
  let component: InterviewPage;
  let fixture: ComponentFixture<InterviewPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InterviewPage],
    }).compileComponents();

    fixture = TestBed.createComponent(InterviewPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
