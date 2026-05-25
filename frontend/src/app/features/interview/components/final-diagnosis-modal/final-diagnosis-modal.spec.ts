import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FinalDiagnosisModal } from './final-diagnosis-modal';

describe('FinalDiagnosisModal', () => {
  let component: FinalDiagnosisModal;
  let fixture: ComponentFixture<FinalDiagnosisModal>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FinalDiagnosisModal],
    }).compileComponents();

    fixture = TestBed.createComponent(FinalDiagnosisModal);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
