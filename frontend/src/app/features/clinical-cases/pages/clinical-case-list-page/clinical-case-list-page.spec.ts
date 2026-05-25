import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClinicalCaseListPage } from './clinical-case-list-page';

describe('ClinicalCaseListPage', () => {
  let component: ClinicalCaseListPage;
  let fixture: ComponentFixture<ClinicalCaseListPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClinicalCaseListPage],
    }).compileComponents();

    fixture = TestBed.createComponent(ClinicalCaseListPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
