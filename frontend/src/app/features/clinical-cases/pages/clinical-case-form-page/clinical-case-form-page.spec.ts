import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClinicalCaseFormPage } from './clinical-case-form-page';

describe('ClinicalCaseFormPage', () => {
  let component: ClinicalCaseFormPage;
  let fixture: ComponentFixture<ClinicalCaseFormPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClinicalCaseFormPage],
    }).compileComponents();

    fixture = TestBed.createComponent(ClinicalCaseFormPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
