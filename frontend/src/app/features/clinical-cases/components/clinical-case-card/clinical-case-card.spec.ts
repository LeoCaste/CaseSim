import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ClinicalCaseCard } from './clinical-case-card';

describe('ClinicalCaseCard', () => {
  let component: ClinicalCaseCard;
  let fixture: ComponentFixture<ClinicalCaseCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClinicalCaseCard],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ClinicalCaseCard);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('clinicalCase', {
      id: 'case-1',
      title: 'Dolor torácico',
      patientName: 'María Pérez',
      status: 'READY',
      factsCount: 3,
      age: 54,
      sex: 'F',
      reason: 'Dolor torácico opresivo',
    });
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
