import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { ClinicalCaseFormPage } from './clinical-case-form-page';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';

describe('ClinicalCaseFormPage', () => {
  let component: ClinicalCaseFormPage;
  let fixture: ComponentFixture<ClinicalCaseFormPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClinicalCaseFormPage],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                has: () => false,
                get: () => null
              }
            }
          }
        },
        {
          provide: Router,
          useValue: { navigate: () => Promise.resolve(true) }
        },
        {
          provide: ClinicalCaseService,
          useValue: {
            getCaseDraft: () => of({
              id: 'draft-1',
              title: '',
              patientName: '',
              status: 'DRAFT',
              factsCount: 0,
              age: 18,
              sex: 'F',
              context: '',
              reason: '',
              currentIllness: '',
              initialMessage: '',
              expectedDiagnosis: '',
              fallbackResponse: '',
              behaviorGuidelines: '',
              personality: {
                tone: 'Natural y colaborador',
                detailLevel: 'Responder solo lo preguntado',
                behaviorNotes: ''
              },
              facts: [],
              clinicalExam: { findings: '' },
              generalBackground: ''
            }),
            create: () => of({ id: 'case-1' })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ClinicalCaseFormPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('allows opening save confirmation for empty DRAFT', () => {
    component.caseFormState.status = 'DRAFT';

    component.openSaveConfirmation();

    expect(component.showSaveModal).toBe(true);
    expect(component.saveError).toBe('');
  });

  it('blocks READY when minimum fields are missing', () => {
    component.caseFormState.status = 'READY';

    component.openSaveConfirmation();

    expect(component.showSaveModal).toBe(false);
    expect(component.saveError).toContain('Para marcar como listo, completa los campos mínimos');
  });
});
