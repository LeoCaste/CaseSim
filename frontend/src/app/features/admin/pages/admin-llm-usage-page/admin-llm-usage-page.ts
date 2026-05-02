import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  LlmUsageDailyMetric,
  LlmUsageFilters,
  LlmUsageStatusFilter,
  LlmUsageSummary
} from '../../../../core/models/admin-llm.model';
import { ADMIN_LLM_ACTIVE_PROVIDERS, LLM_PROVIDER_CATALOG } from '../../../../core/models/llm-provider-catalog';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-admin-llm-usage-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-llm-usage-page.html',
  styleUrl: './admin-llm-usage-page.css'
})
export class AdminLlmUsagePage implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private isFetchingSnapshot = false;
  private readonly integerFormatter = new Intl.NumberFormat('es-CL');
  private readonly decimalUsdFormatter = new Intl.NumberFormat('es-CL', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  private readonly decimalClpFormatter = new Intl.NumberFormat('es-CL', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  });
  private readonly latencySecondsFormatter = new Intl.NumberFormat('es-CL', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  });

  usage: LlmUsageDailyMetric[] = [];
  summary: LlmUsageSummary | null = null;
  isLoading = false;
  loadError = '';

  modelOptions: string[] = [];
  filterForm: { from: string; to: string; model: string; status: LlmUsageStatusFilter } = {
    from: '',
    to: '',
    model: '',
    status: 'all'
  };
  private activeFilters: LlmUsageFilters = {};

  constructor(
    private adminLlmService: AdminLlmService,
    private userContext: UserContext
  ) {
    this.userContext.setRole('admin');
  }

  ngOnInit(): void {
    this.modelOptions = this.buildFallbackModelOptions();
    this.loadSnapshot(this.activeFilters);
  }

  applyFilters(): void {
    if (this.isLoading) {
      return;
    }

    this.loadError = '';
    const validationError = this.validateFilters();
    if (validationError) {
      this.loadError = validationError;
      this.triggerViewUpdate();
      return;
    }

    this.activeFilters = this.buildFiltersFromForm();
    this.loadSnapshot(this.activeFilters);
  }

  clearFilters(): void {
    if (this.isLoading) {
      return;
    }

    this.filterForm = {
      from: '',
      to: '',
      model: '',
      status: 'all'
    };
    this.activeFilters = {};
    this.loadError = '';
    this.loadSnapshot(this.activeFilters);
  }

  hasNoMetrics(): boolean {
    return !this.isLoading && !this.loadError && this.usage.length === 0;
  }

  retryLoad(): void {
    this.loadSnapshot(this.activeFilters);
  }

  hasModelColumn(): boolean {
    return this.usage.some((item) => Boolean((item as LlmUsageDailyMetric & { model?: string }).model));
  }

  hasStatusColumn(): boolean {
    return this.usage.some((item) => Boolean((item as LlmUsageDailyMetric & { status?: string }).status));
  }

  getModelValue(item: LlmUsageDailyMetric): string {
    return (item as LlmUsageDailyMetric & { model?: string }).model ?? '—';
  }

  getStatusValue(item: LlmUsageDailyMetric): string {
    return (item as LlmUsageDailyMetric & { status?: string }).status ?? '—';
  }

  getEstimatedCostUsd(): number {
    const value = this.summary?.estimatedCostUsd ?? 0;
    return Number.isFinite(value) ? value : 0;
  }

  getEstimatedCostClp(): number {
    const value = this.summary?.estimatedCostClp ?? 0;
    return Number.isFinite(value) ? value : 0;
  }

  formatCount(value: number | null | undefined): string {
    const safeValue = Number.isFinite(value) ? Number(value) : 0;
    return this.integerFormatter.format(safeValue);
  }

  formatLatency(value: number | null | undefined): string {
    if (!Number.isFinite(value) || value === null) {
      return '—';
    }

    const latencySeconds = Number(value) / 1000;
    return `${this.latencySecondsFormatter.format(latencySeconds)} s`;
  }

  formatEstimatedCostUsd(): string {
    return `USD $${this.decimalUsdFormatter.format(this.getEstimatedCostUsd())}`;
  }

  formatEstimatedCostClp(): string {
    return `CLP $${this.decimalClpFormatter.format(this.getEstimatedCostClp())}`;
  }

  getErrorInterpretation(): string {
    const summary = this.summary;
    if (!summary || summary.errorCount <= 0) {
      return 'Sin errores registrados';
    }

    const percentage = this.getRatioPercentage(summary.errorCount, summary.totalCalls);
    return `${this.formatCount(summary.errorCount)}/${this.formatCount(summary.totalCalls)} llamadas terminaron con error (${percentage}%)`;
  }

  getFallbackInterpretation(): string {
    const summary = this.summary;
    if (!summary || summary.fallbackCount <= 0) {
      return 'Sin fallback registrado';
    }

    if (summary.totalCalls > 0 && summary.fallbackCount >= summary.totalCalls) {
      return 'Todas las llamadas usaron fallback (100%)';
    }

    const percentage = this.getRatioPercentage(summary.fallbackCount, summary.totalCalls);
    return `${this.formatCount(summary.fallbackCount)}/${this.formatCount(summary.totalCalls)} llamadas usaron fallback (${percentage}%)`;
  }

  getSuccessfulCallsInterpretation(): string {
    const summary = this.summary;
    if (!summary || summary.totalCalls <= 0) {
      return 'Sin llamadas en el período';
    }

    const successfulCalls = Math.max(summary.totalCalls - summary.errorCount - summary.fallbackCount, 0);
    const percentage = this.getRatioPercentage(successfulCalls, summary.totalCalls);
    return `${this.formatCount(successfulCalls)}/${this.formatCount(summary.totalCalls)} llamadas exitosas (${percentage}%)`;
  }

  getActiveFiltersContext(): string {
    const periodContext = this.buildPeriodContext();
    const modelContext = this.activeFilters.model?.trim() ? this.activeFilters.model.trim() : 'Todos los modelos';
    const statusContext = this.getStatusLabel(this.activeFilters.status);

    return `Período: ${periodContext} · Modelo: ${modelContext} · Estado: ${statusContext}`;
  }

  private loadSnapshot(filters: LlmUsageFilters): void {
    if (this.isFetchingSnapshot) {
      return;
    }

    this.isFetchingSnapshot = true;
    this.isLoading = true;
    this.loadError = '';
    this.triggerViewUpdate();

    this.adminLlmService
      .getUsageSnapshot(filters)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isFetchingSnapshot = false;
          this.isLoading = false;
          this.triggerViewUpdate();
        })
      )
      .subscribe({
        next: ({ usage, summary }) => {
          this.usage = usage;
          this.summary = summary;
          this.modelOptions = this.buildModelOptions(usage);
          this.loadError = '';
          this.triggerViewUpdate();
        },
        error: () => {
          this.usage = [];
          this.summary = null;
          this.loadError = 'No se pudieron cargar las métricas LLM. Intenta nuevamente.';
          this.modelOptions = this.buildFallbackModelOptions();
          this.triggerViewUpdate();
        }
      });
  }

  private validateFilters(): string | null {
    if (this.filterForm.from && this.filterForm.to && this.filterForm.from > this.filterForm.to) {
      return 'La fecha desde no puede ser mayor que la fecha hasta.';
    }

    return null;
  }

  private buildFiltersFromForm(): LlmUsageFilters {
    const filters: LlmUsageFilters = {};

    if (this.filterForm.from) {
      filters.from = this.filterForm.from;
    }

    if (this.filterForm.to) {
      filters.to = this.filterForm.to;
    }

    if (this.filterForm.model) {
      filters.model = this.filterForm.model;
    }

    filters.status = this.filterForm.status;

    return filters;
  }

  private buildModelOptions(usage: LlmUsageDailyMetric[]): string[] {
    const modelsFromUsage = Array.from(
      new Set(
        usage
          .map((item) => (item as LlmUsageDailyMetric & { model?: string }).model?.trim())
          .filter((model): model is string => Boolean(model))
      )
    );

    if (modelsFromUsage.length > 0) {
      return modelsFromUsage;
    }

    return this.buildFallbackModelOptions();
  }

  private buildFallbackModelOptions(): string[] {
    const activeProviderModels = ADMIN_LLM_ACTIVE_PROVIDERS.flatMap(
      (provider) => LLM_PROVIDER_CATALOG[provider].knownModels
    );

    const options = Array.from(new Set(activeProviderModels));

    if (!options.includes('llama-3.1-8b-instant')) {
      options.push('llama-3.1-8b-instant');
    }

    return options;
  }

  private buildPeriodContext(): string {
    const from = this.activeFilters.from?.trim() ?? '';
    const to = this.activeFilters.to?.trim() ?? '';

    if (!from && !to) {
      return 'Todos';
    }

    if (from && to) {
      return `${from} – ${to}`;
    }

    if (from) {
      return `desde ${from}`;
    }

    return `hasta ${to}`;
  }

  private getStatusLabel(status: LlmUsageStatusFilter | undefined): string {
    if (!status || status === 'all') {
      return 'Todos los estados';
    }

    if (status === 'error') {
      return 'Error';
    }

    return 'Fallback';
  }

  private getRatioPercentage(value: number, total: number): string {
    if (!Number.isFinite(total) || total <= 0) {
      return '0';
    }

    const percentage = (value / total) * 100;
    return this.latencySecondsFormatter.format(percentage);
  }

  private triggerViewUpdate(): void {
    if (this.destroyRef.destroyed) {
      return;
    }

    this.cdr.detectChanges();
  }
}
