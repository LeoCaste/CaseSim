import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AssignSimulationPage } from './assign-simulation-page';

describe('AssignSimulationPage', () => {
  let component: AssignSimulationPage;
  let fixture: ComponentFixture<AssignSimulationPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignSimulationPage],
    }).compileComponents();

    fixture = TestBed.createComponent(AssignSimulationPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
