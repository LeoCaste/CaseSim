import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfessorReviewPage } from './professor-review-page';

describe('ProfessorReviewPage', () => {
  let component: ProfessorReviewPage;
  let fixture: ComponentFixture<ProfessorReviewPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfessorReviewPage],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfessorReviewPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
