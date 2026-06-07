import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { EvaluationPanel } from './evaluation-panel';

describe('EvaluationPanel', () => {
  let component: EvaluationPanel;
  let fixture: ComponentFixture<EvaluationPanel>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvaluationPanel],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(EvaluationPanel);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('diagnosis', {
      finalDiagnosis: 'Neumonía adquirida en la comunidad',
      reasoning: 'Fiebre, tos productiva y crépitos focales.',
    });
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
