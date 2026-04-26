import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClinicalCaseCard } from './clinical-case-card';

describe('ClinicalCaseCard', () => {
  let component: ClinicalCaseCard;
  let fixture: ComponentFixture<ClinicalCaseCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClinicalCaseCard],
    }).compileComponents();

    fixture = TestBed.createComponent(ClinicalCaseCard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
