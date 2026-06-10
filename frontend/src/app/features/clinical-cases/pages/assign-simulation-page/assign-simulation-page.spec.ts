import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { AssignSimulationPage } from './assign-simulation-page';
import { SimulationAssignmentService } from '../../../../core/services/simulation-assignment.service';
import { UserContext } from '../../../../core/services/user-context';

describe('AssignSimulationPage', () => {
  let component: AssignSimulationPage;
  let fixture: ComponentFixture<AssignSimulationPage>;

  const defaultContext = {
    clinicalCase: {
      id: 'case-1',
      title: 'Caso',
      patientName: 'Paciente',
      reason: 'Dolor',
      status: 'READY' as const
    },
    students: []
  };

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
            getAssignmentContext: () => of(defaultContext),
            createSimulation: () => of({
              id: 'sim-1',
              name: 'Simulación asignada',
              clinicalCaseId: 'case-1',
              clinicalCaseName: 'Caso',
              courseName: 'Curso'
            })
          }
        },
        {
          provide: UserContext,
          useValue: { setRole: () => undefined }
        },
        {
          provide: Router,
          useValue: { navigate: () => Promise.resolve(true) }
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

  it('should show info message when no courses available', () => {
    const noCourseContext = {
      clinicalCase: {
        id: 'case-1',
        title: 'Caso',
        patientName: 'Paciente',
        reason: 'Dolor',
        status: 'READY' as const
      },
      students: [],
      noCourseAvailable: true
    };

    const service = TestBed.inject(SimulationAssignmentService);
    vi.spyOn(service, 'getAssignmentContext').mockReturnValue(of(noCourseContext));

    fixture.detectChanges();
    component.ngOnInit();

    expect(component.showNoCourseWarning).toBe(true);
    expect(component.loadError).toBe('');

    const compiled = fixture.nativeElement as HTMLElement;
    const infoMsg = compiled.querySelector('.assign-feedback--info');
    expect(infoMsg).toBeTruthy();
    expect(infoMsg?.textContent).toContain('No hay cursos disponibles');
  });

  it('should enable create button when students selected and no courses', () => {
    const noCourseContext = {
      clinicalCase: {
        id: 'case-1',
        title: 'Caso',
        patientName: 'Paciente',
        reason: 'Dolor',
        status: 'READY' as const
      },
      students: [
        { id: 's1', name: 'Estudiante 1', status: 'PENDING' as const, selected: true, canReview: false }
      ],
      noCourseAvailable: true
    };

    const service = TestBed.inject(SimulationAssignmentService);
    vi.spyOn(service, 'getAssignmentContext').mockReturnValue(of(noCourseContext));
    vi.spyOn(service, 'createSimulation').mockReturnValue(of({
      id: 'sim-1',
      name: 'Simulación asignada',
      clinicalCaseId: 'case-1',
      clinicalCaseName: 'Caso',
      courseName: 'Curso'
    }));

    fixture.detectChanges();
    component.ngOnInit();

    expect(component.showNoCourseWarning).toBe(true);
    expect(component.canAssignCase).toBe(true);

    component.createSimulation();
    expect(component.showCreateConfirmation).toBe(true);

    component.confirmCreateSimulation();
    expect(component.isCreateSuccess).toBe(true);
    expect(component.loadError).toBe('');
  });
});
