import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClinicalCaseDetailPage } from './clinical-case-detail-page';

describe('ClinicalCaseDetailPage', () => {
  let component: ClinicalCaseDetailPage;
  let fixture: ComponentFixture<ClinicalCaseDetailPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClinicalCaseDetailPage],
    }).compileComponents();

    fixture = TestBed.createComponent(ClinicalCaseDetailPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
