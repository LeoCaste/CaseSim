import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AssignSimulationPage } from './assign-simulation-page';
import { SimulationAssignmentService } from '../../../../core/services/simulation-assignment.service';
import { UserContext } from '../../../../core/services/user-context';

describe('AssignSimulationPage', () => {
  let component: AssignSimulationPage;
  let fixture: ComponentFixture<AssignSimulationPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignSimulationPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => 'case-1'
              }
            }
          }
        },
        {
          provide: SimulationAssignmentService,
          useValue: {
            getAssignmentContext: () => of({ clinicalCase: { id: 'case-1', title: 'Caso', patientName: 'Paciente', reason: 'Dolor', status: 'READY' }, students: [] }),
            createSimulation: () => of({ id: 'sim-1' })
          }
        },
        {
          provide: UserContext,
          useValue: { setRole: () => undefined }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AssignSimulationPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should block assignment when clinical case is not READY', () => {
    component.clinicalCase = {
      id: 'case-1',
      title: 'Caso borrador',
      patientName: 'Paciente',
      reason: 'Dolor',
      status: 'DRAFT'
    };

    component.createSimulation();

    expect(component.showCreateConfirmation).toBe(false);
    expect(component.loadError).toBe('Este caso aún no está listo para ser asignado.');
  });
});
