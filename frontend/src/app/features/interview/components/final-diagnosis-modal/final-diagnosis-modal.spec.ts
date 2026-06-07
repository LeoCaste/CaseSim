import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

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

  it('should disable confirm and show validation hint when diagnosis is blank', () => {
    fixture.detectChanges();

    const primaryButton = fixture.nativeElement.querySelector('.modal-button.primary') as HTMLButtonElement;

    expect(primaryButton.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Escribe un diagnóstico para habilitar el envío.');
  });

  it('should block empty diagnosis confirmation', () => {
    const confirmSpy = vi.fn();
    component.confirm.subscribe(confirmSpy);
    component.diagnosis = '   ';

    component.onConfirm();

    expect(confirmSpy).not.toHaveBeenCalled();
    expect(component.validationMessage).toBe('Escribe un diagnóstico antes de enviarlo.');
  });
});
