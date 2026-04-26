import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCase, ClinicalFact } from '../../../../core/models/clinical-case.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';

@Component({
  selector: 'app-clinical-case-form-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './clinical-case-form-page.html',
  styleUrl: './clinical-case-form-page.css'
})
export class ClinicalCaseFormPage implements OnInit {
  isEditMode = false;
  patientName = 'Catalina Paz Soto';
  showSaveModal = false;
  isSaveSuccess = false;
  isLoading = false;
  loadError = '';

  caseFormState: ClinicalCase = {
    id: '1',
    title: '',
    patientName: '',
    status: 'DRAFT',
    estimatedTimeMinutes: undefined,
    factsCount: 0,
    age: 0,
    sex: 'F',
    context: '',
    reason: '',
    initialMessage: '',
    expectedDiagnosis: '',
    fallbackResponse: '',
    behaviorGuidelines: '',
    facts: []
  };

  clinicalFacts: Array<ClinicalFact & { visibilityLabel: 'Inicial' | 'Bajo pregunta' }> = [];

  constructor(
    private userContext: UserContext,
    private route: ActivatedRoute,
    private router: Router,
    private clinicalCaseService: ClinicalCaseService
  ) {
    this.userContext.setRole('professor');
    this.isEditMode = this.route.snapshot.paramMap.has('id');
  }

  ngOnInit(): void {
    this.isLoading = true;
    if (!this.isEditMode) {
      this.clinicalCaseService.getCaseDraft().subscribe((draft) => {
        this.caseFormState = draft;
        this.syncFromFormState();
        this.isLoading = false;
      });
      return;
    }

    const caseId = this.route.snapshot.paramMap.get('id') ?? '1';
    this.clinicalCaseService.getClinicalCaseById(caseId).subscribe((clinicalCase) => {
      if (clinicalCase) {
        this.caseFormState = clinicalCase;
      }
      this.syncFromFormState();
      this.isLoading = false;
    });
  }

  get pageTitle(): string {
    return this.isEditMode
      ? `Editar caso ${this.patientName}`
      : `Caso ${this.patientName}`;
  }

  openSaveConfirmation(): void {
    this.showSaveModal = true;
    this.isSaveSuccess = false;
  }

  cancelSaveConfirmation(): void {
    this.showSaveModal = false;
    this.isSaveSuccess = false;
  }

  confirmSaveCase(): void {
    this.caseFormState.facts = this.clinicalFacts.map((fact) => ({
      id: fact.id,
      category: fact.category,
      title: fact.title,
      trigger: fact.trigger,
      visibility: fact.visibilityLabel === 'Inicial' ? 'INITIAL' : 'ON_QUESTION'
    }));

    this.clinicalCaseService.upsertClinicalCase(this.caseFormState).subscribe();
    this.isSaveSuccess = true;

    setTimeout(() => {
      this.router.navigate(['/clinical-cases']);
    }, 900);
  }

  get saveLabel(): string {
    return this.isEditMode ? 'Guardar cambios' : 'Guardar caso';
  }

  private syncFromFormState(): void {
    this.patientName = this.caseFormState.patientName;
    this.clinicalFacts = this.caseFormState.facts.map((fact) => ({
      ...fact,
      visibilityLabel: fact.visibility === 'INITIAL' ? 'Inicial' : 'Bajo pregunta'
    }));
  }
}
