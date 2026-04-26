import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UserContext } from '../../../../core/services/user-context';
import {
  ClinicalCase,
  ClinicalCaseUpsertPayload,
  ClinicalFact,
  ClinicalFactVisibility
} from '../../../../core/models/clinical-case.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';

type FactVisibilityLabel = 'Inicial' | 'Bajo pregunta';

@Component({
  selector: 'app-clinical-case-form-page',
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './clinical-case-form-page.html',
  styleUrl: './clinical-case-form-page.css'
})
export class ClinicalCaseFormPage implements OnInit {
  isEditMode = false;
  showSaveModal = false;
  isSaveSuccess = false;
  isLoading = false;
  loadError = '';
  private caseId?: string;

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

  clinicalFacts: Array<ClinicalFact & { visibilityLabel: FactVisibilityLabel }> = [];

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
    this.caseId = this.route.snapshot.paramMap.get('id') ?? undefined;

    if (!this.isEditMode) {
      this.clinicalCaseService.getCaseDraft().subscribe((draft) => {
        this.caseFormState = draft;
        this.syncFromFormState();
        this.isLoading = false;
      });
      return;
    }

    if (!this.caseId) {
      this.loadError = 'No se encontró el caso clínico solicitado.';
      this.isLoading = false;
      return;
    }

    this.clinicalCaseService.getClinicalCaseById(this.caseId).subscribe((clinicalCase) => {
      if (clinicalCase) {
        this.caseFormState = clinicalCase;
      } else {
        this.loadError = 'No se encontró el caso clínico solicitado.';
      }
      this.syncFromFormState();
      this.isLoading = false;
    });
  }

  get pageTitle(): string {
    const patientName = this.caseFormState.patientName || 'sin nombre';
    return this.isEditMode
      ? `Editar caso ${patientName}`
      : `Caso ${patientName}`;
  }

  openSaveConfirmation(): void {
    this.showSaveModal = true;
    this.isSaveSuccess = false;
  }

  cancelSaveConfirmation(): void {
    this.showSaveModal = false;
    this.isSaveSuccess = false;
  }

  saveCase(): void {
    const facts: ClinicalFact[] = this.clinicalFacts.map((fact) => ({
      id: fact.id,
      category: fact.category,
      title: fact.title,
      trigger: fact.trigger,
      visibility: this.mapLabelToVisibility(fact.visibilityLabel)
    }));

    const payload: ClinicalCaseUpsertPayload = {
      title: this.caseFormState.title || `Caso ${this.caseFormState.patientName}`,
      patientName: this.caseFormState.patientName,
      status: this.caseFormState.status,
      estimatedTimeMinutes: this.caseFormState.estimatedTimeMinutes,
      age: this.caseFormState.age,
      sex: this.caseFormState.sex,
      context: this.caseFormState.context,
      reason: this.caseFormState.reason,
      initialMessage: this.caseFormState.initialMessage,
      expectedDiagnosis: this.caseFormState.expectedDiagnosis,
      fallbackResponse: this.caseFormState.fallbackResponse,
      behaviorGuidelines: this.caseFormState.behaviorGuidelines,
      facts
    };

    const save$ = this.isEditMode && this.caseId
      ? this.clinicalCaseService.updateClinicalCase(this.caseId, payload)
      : this.clinicalCaseService.createClinicalCase(payload);

    save$.subscribe();
    this.isSaveSuccess = true;

    setTimeout(() => {
      this.router.navigate(['/clinical-cases']);
    }, 900);
  }

  get saveLabel(): string {
    return this.isEditMode ? 'Guardar cambios' : 'Guardar caso';
  }

  private syncFromFormState(): void {
    this.clinicalFacts = this.caseFormState.facts.map((fact) => ({
      ...fact,
      visibilityLabel: this.mapVisibilityToLabel(fact.visibility)
    }));
  }

  private mapLabelToVisibility(label: string): ClinicalFactVisibility {
    return label === 'Inicial' ? 'INITIAL' : 'ON_QUESTION';
  }

  private mapVisibilityToLabel(visibility: ClinicalFactVisibility): FactVisibilityLabel {
    return visibility === 'INITIAL' ? 'Inicial' : 'Bajo pregunta';
  }
}
