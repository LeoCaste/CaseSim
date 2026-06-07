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
import { EMPTY, catchError, finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ErrorModal } from '../../../../shared/components/error-modal/error-modal';

type FactVisibilityLabel = 'Inicial' | 'Bajo pregunta';

interface FactDraft extends ClinicalFact {
  visibilityLabel: FactVisibilityLabel;
}

interface SaveErrorContext {
  title: string;
  detail: string;
  suggestion: string;
  detailList: string[];
  fieldErrors: Record<string, string>;
}

@Component({
  selector: 'app-clinical-case-form-page',
  imports: [CommonModule, RouterLink, FormsModule, ErrorModal],
  templateUrl: './clinical-case-form-page.html',
  styleUrl: './clinical-case-form-page.css'
})
export class ClinicalCaseFormPage implements OnInit {
  isEditMode = false;
  showSaveModal = false;
  isSaveSuccess = false;
  isSaving = false;
  saveError = '';
  showSaveErrorModal = false;
  saveErrorModalTitle = '';
  saveErrorModalDetail = '';
  saveErrorModalDetailList: string[] = [];
  saveErrorModalSuggestion = '';
  isLoading = false;
  loadError = '';
  saveAttempted = false;
  private caseId?: string;
  private readonly destroyRef = inject(DestroyRef);

  caseFormState: ClinicalCase = {
    id: '1',
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
    clinicalExam: {
      findings: ''
    },
    generalBackground: ''
  };

  clinicalFacts: FactDraft[] = [];
  factsValidationError = '';
  fieldErrors: Record<string, string> = {};
  expandedSections: Set<string> = new Set(['block-a']);
  private readonly fieldToSection: Record<string, string> = {
    patientName: 'block-a',
    age: 'block-a',
    sex: 'block-a',
    context: 'block-a',
    initialMessage: 'block-a',
    reason: 'block-a',
    fallbackResponse: 'block-d',
    expectedDiagnosis: 'block-e',
    behaviorGuidelines: 'block-d',
    generalBackground: 'block-b'
  };
  private readonly fieldPathToControlName: Record<string, string> = {
    patientName: 'patientName',
    age: 'age',
    sex: 'sex',
    context: 'context',
    reason: 'reason',
    initialMessage: 'initialMessage',
    expectedDiagnosis: 'expectedDiagnosis',
    fallbackResponse: 'fallbackResponse',
    behaviorGuidelines: 'behaviorGuidelines'
  };

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

    if (!this.validateFacts()) {
      this.saveAttempted = true;
      this.saveError = this.factsValidationError;
      this.expandSectionsWithErrors();
      this.focusFirstInvalidField();
      this.cdr.detectChanges();
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

    if (!this.validateFacts()) {
      this.saveAttempted = true;
      this.showSaveModal = false;
      this.saveError = this.factsValidationError;
      this.expandSectionsWithErrors();
      this.focusFirstInvalidField();
      this.cdr.detectChanges();
      return;
    }

    const facts: ClinicalFact[] = this.clinicalFacts.map((fact) => ({
      id: fact.id,
      category: fact.category.trim(),
      title: fact.title.trim(),
      content: fact.content.trim(),
      trigger: fact.trigger.trim(),
      visibility: this.mapLabelToVisibility(fact.visibilityLabel ?? this.mapVisibilityToLabel(fact.visibility)),
      revealLevel: fact.revealLevel ?? (fact.visibility === 'INITIAL' ? 1 : 2)
    }));

    const payload: ClinicalCaseUpsertPayload = {
      title: (this.caseFormState.title || `Caso ${this.caseFormState.patientName}`).trim(),
      patientName: this.caseFormState.patientName.trim(),
      status: this.caseFormState.status,
      age: this.caseFormState.age,
      sex: this.caseFormState.sex,
      context: this.caseFormState.context.trim(),
      reason: this.caseFormState.reason.trim(),
      currentIllness: this.caseFormState.currentIllness?.trim() || undefined,
      initialMessage: this.caseFormState.initialMessage.trim(),
      expectedDiagnosis: this.caseFormState.expectedDiagnosis?.trim(),
      legacyExpectedDiagnosis: this.caseFormState.expectedDiagnosis?.trim(),
      fallbackResponse: this.caseFormState.fallbackResponse?.trim(),
      behaviorGuidelines: this.caseFormState.behaviorGuidelines?.trim(),
      personality: this.caseFormState.personality,
      facts,
      generalBackground: this.caseFormState.generalBackground?.trim() || undefined,
      clinicalExam: this.caseFormState.clinicalExam
    };

    const save$ = this.isEditMode && this.caseId
      ? this.clinicalCaseService.update(this.caseId, payload)
      : this.clinicalCaseService.create(payload);

    this.isSaving = true;
    this.saveError = '';
    this.saveErrorModalDetailList = [];

    save$
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        catchError((error: unknown) => {
          this.isSaveSuccess = false;
          this.showSaveModal = false;
          const saveErrorContext = this.buildSaveErrorContext(error);
          this.saveError = saveErrorContext.title;
          this.saveErrorModalTitle = saveErrorContext.title;
          this.saveErrorModalDetail = saveErrorContext.detail;
          this.saveErrorModalDetailList = saveErrorContext.detailList;
          this.saveErrorModalSuggestion = saveErrorContext.suggestion;
          this.fieldErrors = saveErrorContext.fieldErrors;
          this.showSaveErrorModal = true;
          this.focusFirstInvalidField();
          this.cdr.detectChanges();
          return EMPTY;
        }),
        finalize(() => {
          this.isSaving = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.isSaveSuccess = true;
          this.showSaveModal = false;
          this.showSaveErrorModal = false;
          this.fieldErrors = {};
          void this.router.navigate(['/clinical-cases']);
        }
      });
  }

  closeSaveErrorModal(): void {
    this.showSaveErrorModal = false;
  }

  retrySaveFromErrorModal(): void {
    this.closeSaveErrorModal();
    this.saveCase();
  }

  hasFieldError(path: string): boolean {
    return Boolean(this.fieldErrors[path]);
  }

  getFieldError(path: string): string {
    return this.fieldErrors[path] ?? '';
  }

  clearFieldError(path: string): void {
    if (!this.fieldErrors[path]) {
      return;
    }

    const nextErrors = { ...this.fieldErrors };
    delete nextErrors[path];
    this.fieldErrors = nextErrors;
  }

  toggleSection(sectionId: string): void {
    if (this.expandedSections.has(sectionId)) {
      this.expandedSections.delete(sectionId);
    } else {
      this.expandedSections.add(sectionId);
    }
  }

  isSectionExpanded(sectionId: string): boolean {
    return this.expandedSections.has(sectionId);
  }

  getBlockIndicator(blockId: string): string {
    switch (blockId) {
      case 'block-a': {
        const missing: string[] = [];
        if (!this.caseFormState.patientName.trim()) missing.push('patientName');
        if (!Number.isFinite(this.caseFormState.age) || this.caseFormState.age <= 0) missing.push('age');
        if (!this.caseFormState.reason.trim()) missing.push('reason');
        if (!this.caseFormState.initialMessage.trim()) missing.push('initialMessage');
        return missing.length === 0 ? 'Completo' : 'Incompleto';
      }
      case 'block-b':
        return 'Opcional';
      case 'block-c': {
        if (this.clinicalFacts.length === 0) return 'Sin datos revelables';
        const missingFacts = this.clinicalFacts.some(
          (f) => !f.category.trim() || !f.title.trim() || !f.content.trim() || !f.trigger.trim()
        );
        return missingFacts ? 'Incompleto' : 'Completo';
      }
      case 'block-d': {
        if (!this.caseFormState.fallbackResponse?.trim()) return 'Incompleto';
        return 'Completo';
      }
      case 'block-e':
        return 'Opcional';
      default:
        return '';
    }
  }

  getBlockIndicatorClass(blockId: string): string {
    switch (blockId) {
      case 'block-a': {
        const missing: string[] = [];
        if (!this.caseFormState.patientName.trim()) missing.push('patientName');
        if (!Number.isFinite(this.caseFormState.age) || this.caseFormState.age <= 0) missing.push('age');
        if (!this.caseFormState.reason.trim()) missing.push('reason');
        if (!this.caseFormState.initialMessage.trim()) missing.push('initialMessage');
        return missing.length === 0 ? 'indicator-complete' : 'indicator-incomplete';
      }
      case 'block-b':
        return 'indicator-optional';
      case 'block-c': {
        if (this.clinicalFacts.length === 0) return '';
        const missingFacts = this.clinicalFacts.some(
          (f) => !f.category.trim() || !f.title.trim() || !f.content.trim() || !f.trigger.trim()
        );
        return missingFacts ? 'indicator-incomplete' : 'indicator-complete';
      }
      case 'block-d': {
        if (!this.caseFormState.fallbackResponse?.trim()) return 'indicator-incomplete';
        return 'indicator-complete';
      }
      case 'block-e':
        return 'indicator-optional';
      default:
        return '';
    }
  }

  goToSection(sectionId: string): void {
    this.expandedSections.add(sectionId);
    setTimeout(() => {
      const element = document.getElementById(sectionId);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  }

  addFact(): void {
    this.saveError = '';
    this.factsValidationError = '';
    this.clinicalFacts = [
      ...this.clinicalFacts,
      {
        category: '',
        title: '',
        content: '',
        trigger: '',
        visibility: 'ON_QUESTION',
        revealLevel: 2,
        visibilityLabel: 'Bajo pregunta'
      }
    ];
  }

  removeFact(index: number): void {
    this.saveError = '';
    this.factsValidationError = '';

    if (index < 0 || index >= this.clinicalFacts.length) {
      return;
    }

    this.clinicalFacts = this.clinicalFacts.filter((_, currentIndex) => currentIndex !== index);
  }

  updateFactVisibility(index: number, label: string): void {
    const fact = this.clinicalFacts[index];
    if (!fact) {
      return;
    }

    const normalizedLabel: FactVisibilityLabel = label === 'Inicial' ? 'Inicial' : 'Bajo pregunta';
    const updatedFact: FactDraft = {
      ...fact,
      visibilityLabel: normalizedLabel,
      visibility: this.mapLabelToVisibility(normalizedLabel),
      revealLevel:
        normalizedLabel === this.mapVisibilityToLabel(fact.visibility)
          ? fact.revealLevel ?? (normalizedLabel === 'Inicial' ? 1 : 2)
          : normalizedLabel === 'Inicial' ? 1 : 2
    };

    this.clinicalFacts = this.clinicalFacts.map((currentFact, currentIndex) =>
      currentIndex === index ? updatedFact : currentFact
    );
    this.clearFieldError(`facts.${index}.visibility`);
  }

  trackByFactIndex(index: number): number {
    return index;
  }

  get saveLabel(): string {
    return this.isEditMode ? 'Guardar cambios' : 'Guardar caso';
  }

  get pendingRequiredItems(): string[] {
    const pending: string[] = [];

    if (!this.caseFormState.patientName.trim()) {
      pending.push('Nombre del paciente');
    }

    if (!Number.isFinite(this.caseFormState.age) || this.caseFormState.age <= 0) {
      pending.push('Edad del paciente (mayor a 0)');
    }

    if (!this.caseFormState.reason.trim()) {
      pending.push('Motivo principal de consulta');
    }

    if (!this.caseFormState.initialMessage.trim()) {
      pending.push('Mensaje inicial del paciente');
    }

    if (!this.caseFormState.sex) {
      pending.push('Sexo del paciente');
    }

    if (!this.caseFormState.fallbackResponse?.trim()) {
      pending.push('Respuesta cuando no sabe algo');
    }

    if (this.clinicalFacts.length === 0) {
      pending.push('Al menos un elemento de información revelable');
      return pending;
    }

    this.clinicalFacts.forEach((fact, index) => {
      const factNumber = index + 1;

      if (!fact.category.trim()) {
        pending.push(`Información revelable ${factNumber}: categoría`);
      }
      if (!fact.title.trim()) {
        pending.push(`Información revelable ${factNumber}: título`);
      }
      if (!fact.content.trim()) {
        pending.push(`Información revelable ${factNumber}: contenido`);
      }
      if (!fact.trigger.trim()) {
        pending.push(`Información revelable ${factNumber}: gatillante`);
      }
    });

    return pending;
  }

  get shouldShowPendingRequiredSummary(): boolean {
    return this.saveAttempted && this.pendingRequiredItems.length > 0;
  }

  private syncFromFormState(): void {
    this.caseFormState.personality = this.caseFormState.personality ?? {
      tone: 'Natural y colaborador',
      detailLevel: 'Responder solo lo preguntado',
      behaviorNotes: this.caseFormState.behaviorGuidelines ?? ''
    };

    this.caseFormState.clinicalExam = this.caseFormState.clinicalExam ?? {
      findings: ''
    };

    this.caseFormState.generalBackground = this.caseFormState.generalBackground ?? '';

    this.clinicalFacts = this.caseFormState.facts.map((fact) => ({
      ...fact,
      content: fact.content ?? '',
      visibility: fact.visibility ?? 'ON_QUESTION',
      revealLevel: fact.revealLevel ?? (fact.visibility === 'INITIAL' ? 1 : 2),
      visibilityLabel: this.mapVisibilityToLabel(fact.visibility ?? 'ON_QUESTION')
    }));

    this.factsValidationError = '';
  }

  private validateFacts(): boolean {
    this.fieldErrors = {};

    if (this.caseFormState.status !== 'READY') {
      this.factsValidationError = '';
      return true;
    }

    if (!this.validateCaseRequiredFields()) {
      return false;
    }

    if (this.clinicalFacts.length === 0) {
      this.factsValidationError = 'Agrega al menos un elemento de información revelable antes de guardar el caso clínico.';
      return false;
    }

    const invalidFact = this.clinicalFacts.findIndex(
      (fact) => !fact.category.trim() || !fact.title.trim() || !fact.content.trim() || !fact.trigger.trim()
    );

    if (invalidFact >= 0) {
      this.factsValidationError =
        'Cada elemento de información revelable debe tener categoría, título, contenido y gatillante para poder guardar.';
      const current = this.clinicalFacts[invalidFact];
      if (!current.category.trim()) this.fieldErrors[`facts.${invalidFact}.category`] = 'Campo obligatorio';
      if (!current.title.trim()) this.fieldErrors[`facts.${invalidFact}.title`] = 'Campo obligatorio';
      if (!current.content.trim()) this.fieldErrors[`facts.${invalidFact}.content`] = 'Campo obligatorio';
      if (!current.trigger.trim()) this.fieldErrors[`facts.${invalidFact}.trigger`] = 'Campo obligatorio';
      return false;
    }

    const hasInvalidVisibility = this.clinicalFacts.some((fact) => {
      const resolvedVisibility = this.mapLabelToVisibility(
        fact.visibilityLabel ?? this.mapVisibilityToLabel(fact.visibility)
      );

      return resolvedVisibility !== 'INITIAL' && resolvedVisibility !== 'ON_QUESTION';
    });

    if (hasInvalidVisibility) {
      this.factsValidationError =
        'Cada elemento de información revelable debe tener una visibilidad válida (Inicial o Bajo pregunta).';
      return false;
    }

    this.factsValidationError = '';
    return true;
  }

  private validateCaseRequiredFields(): boolean {
    if (!this.caseFormState.patientName.trim()) {
      this.factsValidationError = 'Ingresa el nombre del paciente antes de guardar.';
      this.fieldErrors['patientName'] = 'Campo obligatorio';
      return false;
    }

    if (!Number.isFinite(this.caseFormState.age) || this.caseFormState.age <= 0) {
      this.factsValidationError = 'La edad del paciente debe ser mayor a 0.';
      this.fieldErrors['age'] = 'Debe ser mayor a 0';
      return false;
    }

    if (!this.caseFormState.reason.trim()) {
      this.factsValidationError = 'Ingresa el motivo principal de consulta antes de guardar.';
      this.fieldErrors['reason'] = 'Campo obligatorio';
      return false;
    }

    if (!this.caseFormState.initialMessage.trim()) {
      this.factsValidationError = 'Ingresa el mensaje inicial del paciente antes de guardar.';
      this.fieldErrors['initialMessage'] = 'Campo obligatorio';
      return false;
    }

    if (!this.caseFormState.sex) {
      this.factsValidationError = 'Selecciona el sexo del paciente antes de marcar el caso como listo.';
      this.fieldErrors['sex'] = 'Campo obligatorio';
      return false;
    }

    if (!this.caseFormState.fallbackResponse?.trim()) {
      this.factsValidationError = 'Ingresa cómo responde el paciente cuando no sabe algo antes de marcar el caso como listo.';
      this.fieldErrors['fallbackResponse'] = 'Campo obligatorio';
      return false;
    }

    return true;
  }

  private mapLabelToVisibility(label: string): ClinicalFactVisibility {
    return label === 'Inicial' ? 'INITIAL' : 'ON_QUESTION';
  }

  private mapVisibilityToLabel(visibility: ClinicalFactVisibility): FactVisibilityLabel {
    return visibility === 'INITIAL' ? 'Inicial' : 'Bajo pregunta';
  }

  private buildSaveErrorContext(error: unknown): SaveErrorContext {
    const resolvedStatus = this.resolveHttpStatus(error);
    const backendMessage = this.resolveBackendErrorMessage(error);
    const backendDetails = this.resolveBackendErrorDetails(error);
    const mappedFieldErrors = this.mapBackendDetailsToFieldErrors(backendDetails);
    const detailList = backendDetails.map((detail) => `${detail.path}: ${detail.message}`);

    if (resolvedStatus === 400) {
      return {
        title: 'No se pudo guardar el caso',
        detail: backendMessage,
        suggestion: 'Revisa antecedentes y campos obligatorios antes de reintentar.',
        detailList,
        fieldErrors: mappedFieldErrors
      };
    }

    if (resolvedStatus === 401) {
      return {
        title: 'Sesión expirada',
        detail: backendMessage,
        suggestion: 'Inicia sesión nuevamente y vuelve a intentar el guardado.',
        detailList,
        fieldErrors: mappedFieldErrors
      };
    }

    if (resolvedStatus === 403) {
      return {
        title: 'Sin permisos para guardar',
        detail: backendMessage,
        suggestion: 'Tu perfil no tiene permisos para esta acción. Contacta al administrador si corresponde.',
        detailList,
        fieldErrors: mappedFieldErrors
      };
    }

    if (resolvedStatus === 404) {
      return {
        title: 'Caso clínico no disponible',
        detail: backendMessage,
        suggestion: 'Vuelve al listado, confirma que el caso existe y vuelve a editar.',
        detailList,
        fieldErrors: mappedFieldErrors
      };
    }

    if (resolvedStatus === 0) {
      return {
        title: 'Sin conexión con el servidor',
        detail: backendMessage,
        suggestion: 'Verifica tu conexión e inténtalo nuevamente.',
        detailList,
        fieldErrors: mappedFieldErrors
      };
    }

    if (resolvedStatus !== null && resolvedStatus >= 500) {
      return {
        title: 'Error interno del servidor',
        detail: backendMessage,
        suggestion: 'Intenta nuevamente en unos minutos. Si persiste, reporta el incidente.',
        detailList,
        fieldErrors: mappedFieldErrors
      };
    }

    return {
      title: 'No se pudo guardar el caso',
      detail: backendMessage,
      suggestion: 'Revisa los datos ingresados y vuelve a intentar.',
      detailList,
      fieldErrors: mappedFieldErrors
    };
  }

  private resolveBackendErrorDetails(error: unknown): Array<{ path: string; message: string }> {
    if (!(error instanceof HttpErrorResponse)) {
      return [];
    }

    const backendPayload = error.error as
      | {
          details?: unknown;
          error?: { details?: unknown };
        }
      | null
      | undefined;

    const detailsCandidate = backendPayload?.details ?? backendPayload?.error?.details;
    if (!detailsCandidate) {
      return [];
    }

    if (Array.isArray(detailsCandidate)) {
      return detailsCandidate
        .map((item) => {
          if (typeof item === 'string') {
            const raw = item.trim();
            if (!raw) {
              return null;
            }
            const [path, ...rest] = raw.split(':');
            const message = rest.join(':').trim();
            return {
              path: path.trim(),
              message: message || 'Valor inválido'
            };
          }

          if (item && typeof item === 'object') {
            const detail = item as { path?: unknown; field?: unknown; message?: unknown; error?: unknown };
            const path = (typeof detail.path === 'string' ? detail.path : detail.field)?.toString().trim();
            const message = (typeof detail.message === 'string' ? detail.message : detail.error)
              ?.toString()
              .trim();

            if (!path || !message) {
              return null;
            }

            return { path, message };
          }

          return null;
        })
        .filter((item): item is { path: string; message: string } => item !== null);
    }

    if (detailsCandidate && typeof detailsCandidate === 'object') {
      return Object.entries(detailsCandidate as Record<string, unknown>)
        .map(([path, value]) => {
          const message = typeof value === 'string' ? value.trim() : '';
          if (!message) {
            return null;
          }
          return { path, message };
        })
        .filter((item): item is { path: string; message: string } => item !== null);
    }

    return [];
  }

  private mapBackendDetailsToFieldErrors(details: Array<{ path: string; message: string }>): Record<string, string> {
    const mappedErrors: Record<string, string> = {};

    for (const detail of details) {
      const normalizedPath = this.normalizeErrorPath(detail.path);
      if (!normalizedPath || mappedErrors[normalizedPath]) {
        continue;
      }
      mappedErrors[normalizedPath] = detail.message;
    }

    return mappedErrors;
  }

  private normalizeErrorPath(path: string): string {
    const trimmedPath = path.trim();
    if (!trimmedPath) {
      return '';
    }

    const factPathMatch = trimmedPath.match(/^facts\[(\d+)]\.(category|title|content|trigger|visibility|reveallevel)$/i);
    if (factPathMatch) {
      const rawField = factPathMatch[2].toLowerCase();
      const field = rawField === 'reveallevel' ? 'visibility' : rawField;
      return `facts.${factPathMatch[1]}.${field}`;
    }

    const aliases: Record<string, string> = {
      title: 'title',
      patientname: 'patientName',
      patientage: 'age',
      age: 'age',
      patientsex: 'sex',
      sex: 'sex',
      chiefcomplaint: 'reason',
      reason: 'reason',
      initialmessage: 'initialMessage',
      expecteddiagnosis: 'expectedDiagnosis',
      fallbackresponse: 'fallbackResponse',
      behaviorguidelines: 'behaviorGuidelines',
      context: 'context',
      reveallevel: 'visibility'
    };

    const key = trimmedPath.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
    return aliases[key] ?? trimmedPath;
  }

  private resolveBackendErrorMessage(error: unknown): string {
    const fallback = 'No fue posible guardar el caso clínico. Revisa los datos e inténtalo nuevamente.';

    if (error instanceof HttpErrorResponse) {
      const backendPayload = error.error as
        | { message?: unknown; error?: { message?: unknown } }
        | string
        | null
        | undefined;

      if (typeof backendPayload === 'string' && backendPayload.trim()) {
        return backendPayload;
      }

      if (backendPayload && typeof backendPayload === 'object') {
        const directMessage = backendPayload.message;
        if (typeof directMessage === 'string' && directMessage.trim()) {
          return directMessage;
        }

        const nestedMessage = backendPayload.error?.message;
        if (typeof nestedMessage === 'string' && nestedMessage.trim()) {
          return nestedMessage;
        }
      }

      if (typeof error.message === 'string' && error.message.trim()) {
        return error.message;
      }
    }

    if (typeof error === 'object' && error !== null && 'message' in error) {
      const candidate = (error as { message?: unknown }).message;
      if (typeof candidate === 'string' && candidate.trim()) {
        return candidate;
      }
    }

    return fallback;
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

  private expandSectionsWithErrors(): void {
    for (const fieldPath of Object.keys(this.fieldErrors)) {
      const sectionId = this.getSectionForField(fieldPath);
      if (sectionId) {
        this.expandedSections.add(sectionId);
      }
    }
  }

  private getSectionForField(fieldPath: string): string | null {
    if (fieldPath.startsWith('facts.')) {
      return 'block-c';
    }
    return this.fieldToSection[fieldPath] ?? null;
  }

  private focusFirstInvalidField(): void {
    const [firstPath] = Object.keys(this.fieldErrors);
    if (!firstPath) {
      return;
    }

    const sectionId = this.getSectionForField(firstPath);
    if (sectionId) {
      this.expandedSections.add(sectionId);
    }

    const controlName = this.mapFieldPathToControlName(firstPath);
    if (!controlName) {
      return;
    }

    setTimeout(() => {
      const target = document.querySelector(`[name="${controlName}"]`) as HTMLElement | null;
      if (!target) {
        return;
      }

      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      target.focus();
    });
  }

  private mapFieldPathToControlName(path: string): string {
    const factMatch = path.match(/^facts\.(\d+)\.(category|title|content|trigger|visibility)$/);
    if (factMatch) {
      const [, index, field] = factMatch;
      const factNameMap: Record<string, string> = {
        category: `factCategory${index}`,
        title: `factTitle${index}`,
        content: `factContent${index}`,
        trigger: `factTrigger${index}`,
        visibility: `factVisibility${index}`
      };
      return factNameMap[field] ?? '';
    }

    return this.fieldPathToControlName[path] ?? '';
  }
}
