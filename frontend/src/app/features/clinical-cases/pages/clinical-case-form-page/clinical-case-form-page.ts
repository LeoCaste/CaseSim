import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ClinicalCase,
  ClinicalCaseUpsertPayload,
  ClinicalFact,
  ClinicalFactVisibility
} from '../../../../core/models/clinical-case.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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
  isSaving = false;
  saveError = '';
  isLoading = false;
  loadError = '';
  private caseId?: string;
  private readonly destroyRef = inject(DestroyRef);

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
    personality: {
      tone: 'Natural y colaborador',
      detailLevel: 'Responder solo lo preguntado',
      behaviorNotes: ''
    },
    facts: []
  };

  clinicalFacts: Array<ClinicalFact & { visibilityLabel: FactVisibilityLabel }> = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private clinicalCaseService: ClinicalCaseService,
    private cdr: ChangeDetectorRef
  ) {
    this.isEditMode = this.route.snapshot.paramMap.has('id');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.caseId = this.route.snapshot.paramMap.get('id') ?? undefined;

    if (!this.isEditMode) {
      this.clinicalCaseService
        .getCaseDraft()
        .pipe(
          take(1),
          takeUntilDestroyed(this.destroyRef),
          finalize(() => {
            this.isLoading = false;
            this.cdr.detectChanges();
          })
        )
        .subscribe({
          next: (draft) => {
            this.caseFormState = draft;
            this.syncFromFormState();
            this.cdr.detectChanges();
          },
          error: () => {
            this.loadError = 'No fue posible cargar el borrador del caso clínico.';
            this.cdr.detectChanges();
          }
        });
      return;
    }

    if (!this.caseId) {
      this.loadError = 'No se encontró el caso clínico solicitado.';
      this.isLoading = false;
      return;
    }

    this.clinicalCaseService
      .getById(this.caseId)
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (clinicalCase) => {
          if (clinicalCase) {
            this.caseFormState = clinicalCase;
          } else {
            this.loadError = 'No se encontró el caso clínico solicitado.';
          }
          this.syncFromFormState();
          this.cdr.detectChanges();
        },
        error: (error: unknown) => {
          this.loadError = this.buildLoadErrorMessage(error);
          this.cdr.detectChanges();
        }
      });
  }

  get pageTitle(): string {
    const patientName = this.caseFormState.patientName || 'sin nombre';
    return this.isEditMode
      ? `Editar caso ${patientName}`
      : `Caso ${patientName}`;
  }

  openSaveConfirmation(): void {
    if (this.isSaving) {
      return;
    }

    this.showSaveModal = true;
    this.isSaveSuccess = false;
    this.saveError = '';
  }

  cancelSaveConfirmation(): void {
    if (this.isSaving) {
      return;
    }

    this.showSaveModal = false;
    this.isSaveSuccess = false;
    this.saveError = '';
  }

  saveCase(): void {
    if (this.isSaving) {
      return;
    }

    if (this.isEditMode && !this.caseId) {
      this.saveError = 'No se encontró el identificador del caso clínico para guardar los cambios.';
      this.showSaveModal = false;
      return;
    }

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
      personality: this.caseFormState.personality,
      facts
    };

    const save$ = this.isEditMode && this.caseId
      ? this.clinicalCaseService.update(this.caseId, payload)
      : this.clinicalCaseService.create(payload);

    this.isSaving = true;
    this.saveError = '';

    save$
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSaving = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.isSaveSuccess = true;
          this.showSaveModal = false;
          void this.router.navigate(['/clinical-cases']);
        },
        error: (error: unknown) => {
          this.isSaveSuccess = false;
          this.showSaveModal = false;
          this.saveError = this.buildSaveErrorMessage(error);
          this.cdr.detectChanges();
        }
      });
  }

  get saveLabel(): string {
    return this.isEditMode ? 'Guardar cambios' : 'Guardar caso';
  }

  private syncFromFormState(): void {
    this.caseFormState.personality = this.caseFormState.personality ?? {
      tone: 'Natural y colaborador',
      detailLevel: 'Responder solo lo preguntado',
      behaviorNotes: this.caseFormState.behaviorGuidelines ?? ''
    };

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

  private buildSaveErrorMessage(error: unknown): string {
    const resolvedStatus = this.resolveHttpStatus(error);

    if (resolvedStatus === 401) {
      return 'Tu sesión expiró. Inicia sesión nuevamente para guardar este caso.';
    }

    if (resolvedStatus === 403) {
      return 'Tu cuenta no tiene permisos para actualizar este caso clínico.';
    }

    if (resolvedStatus === 404) {
      return 'El caso clínico que intentas actualizar ya no existe o no está disponible.';
    }

    if (resolvedStatus === 0) {
      return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
    }

    if (resolvedStatus !== null && resolvedStatus >= 500) {
      return 'Ocurrió un problema interno del servidor al guardar el caso clínico. Inténtalo nuevamente en unos minutos.';
    }

    return 'No fue posible guardar el caso clínico. Revisa los datos e inténtalo nuevamente.';
  }

  private buildLoadErrorMessage(error: unknown): string {
    const resolvedStatus = this.resolveHttpStatus(error);

    if (resolvedStatus === 401) {
      return 'Tu sesión expiró. Inicia sesión nuevamente para continuar.';
    }

    if (resolvedStatus === 403) {
      return 'Tu cuenta no tiene permisos para ver este caso clínico.';
    }

    if (resolvedStatus === 404) {
      return 'No se encontró el caso clínico solicitado.';
    }

    if (resolvedStatus === 0) {
      return 'No fue posible conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
    }

    if (resolvedStatus !== null && resolvedStatus >= 500) {
      return 'Ocurrió un problema interno del servidor al cargar el caso clínico. Inténtalo nuevamente en unos minutos.';
    }

    return 'No fue posible cargar el caso clínico.';
  }

  private resolveHttpStatus(error: unknown): number | null {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.status === 'number') {
        return error.status;
      }

      const nestedStatus = (error.error as { status?: unknown } | null | undefined)?.status;
      if (typeof nestedStatus === 'number') {
        return nestedStatus;
      }

      if (typeof nestedStatus === 'string') {
        const parsedStatus = Number.parseInt(nestedStatus, 10);
        return Number.isFinite(parsedStatus) ? parsedStatus : null;
      }
    }

    if (typeof error === 'object' && error !== null && 'status' in error) {
      const candidate = (error as { status?: unknown }).status;
      if (typeof candidate === 'number') {
        return candidate;
      }
    }

    return null;
  }
}
