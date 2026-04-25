import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfessorDashboardPage } from './professor-dashboard-page';

describe('ProfessorDashboardPage', () => {
  let component: ProfessorDashboardPage;
  let fixture: ComponentFixture<ProfessorDashboardPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfessorDashboardPage],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfessorDashboardPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
