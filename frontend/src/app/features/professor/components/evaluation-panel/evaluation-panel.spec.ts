import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EvaluationPanel } from './evaluation-panel';

describe('EvaluationPanel', () => {
  let component: EvaluationPanel;
  let fixture: ComponentFixture<EvaluationPanel>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvaluationPanel],
    }).compileComponents();

    fixture = TestBed.createComponent(EvaluationPanel);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
