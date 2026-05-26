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
  rawUsage: LlmUsageDailyMetric[] = [];
  summary: LlmUsageSummary | null = null;
  isLoading = false;
  loadError = '';

  providerOptions: string[] = [];
  modelOptions: string[] = [];
  filterForm: { from: string; to: string; provider: string; model: string; status: LlmUsageStatusFilter } = {
    from: '',
    to: '',
    provider: '',
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
    this.providerOptions = [];
    this.modelOptions = [];
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
      provider: '',
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

  hasActiveFiltersApplied(): boolean {
    return Boolean(
      this.activeFilters.from
      || this.activeFilters.to
      || this.activeFilters.model
      || this.activeFilters.status
      || this.filterForm.provider.trim()
    );
  }

  retryLoad(): void {
    this.loadSnapshot(this.activeFilters);
  }

  onProviderFilterChange(): void {
    this.modelOptions = this.buildModelOptions(this.rawUsage);

    if (!this.filterForm.provider.trim()) {
      this.filterForm.model = '';
      return;
    }

    if (this.filterForm.model && !this.modelOptions.includes(this.filterForm.model)) {
      this.filterForm.model = '';
    }
  }

  shouldShowModelFilter(): boolean {
    return Boolean(this.filterForm.provider.trim());
  }

  hasModelColumn(): boolean {
    return this.usage.some((item) => Boolean(item.model));
  }

  hasProviderColumn(): boolean {
    return this.usage.some((item) => Boolean(item.provider));
  }

  hasStatusColumn(): boolean {
    return this.usage.some((item) => Boolean(item.status));
  }

  getModelValue(item: LlmUsageDailyMetric): string {
    return item.model ?? '—';
  }

  getProviderValue(item: LlmUsageDailyMetric): string {
    return item.provider ?? '—';
  }

  getStatusValue(item: LlmUsageDailyMetric): string {
    return item.status ?? '—';
  }

  areTokensEstimated(): boolean {
    if (!this.usage.length) {
      return false;
    }

    return this.usage.some((item) => item.tokenEstimated === true);
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
    const providerContext = this.filterForm.provider.trim()
      ? this.filterForm.provider.trim()
      : this.getProviderFilterPlaceholder();
    const modelContext = this.filterForm.model.trim()
      ? this.filterForm.model.trim()
      : this.getModelFilterPlaceholder();
    const statusContext = this.getStatusLabel(this.activeFilters.status);

    return `Período: ${periodContext} · Proveedor: ${providerContext} · Modelo: ${modelContext} · Estado: ${statusContext}`;
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
          this.rawUsage = usage;
          this.providerOptions = this.buildProviderOptions(this.rawUsage);
          this.modelOptions = this.buildModelOptions(this.rawUsage);

          if (this.filterForm.provider && !this.providerOptions.includes(this.filterForm.provider)) {
            this.filterForm.provider = '';
          }
          if (this.filterForm.model && !this.modelOptions.includes(this.filterForm.model)) {
            this.filterForm.model = '';
          }

          this.usage = this.applyClientFilters(this.rawUsage);
          this.summary = this.buildClientSummary(this.usage, summary);
          this.loadError = '';
          this.triggerViewUpdate();
        },
        error: () => {
          this.rawUsage = [];
          this.usage = [];
          this.summary = null;
          this.loadError = 'No se pudieron cargar las métricas LLM. Intenta nuevamente.';
          this.providerOptions = [];
          this.modelOptions = [];
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

    if (this.filterForm.status !== 'all') {
      filters.status = this.filterForm.status;
    }

    return filters;
  }

  private applyClientFilters(usage: LlmUsageDailyMetric[]): LlmUsageDailyMetric[] {
    const providerFilter = this.filterForm.provider.trim().toLowerCase();
    const modelFilter = this.filterForm.model.trim().toLowerCase();

    return usage.filter((item) => {
      const providerMatches = !providerFilter || (item.provider?.trim().toLowerCase() ?? '') === providerFilter;
      const modelMatches = !modelFilter || (item.model?.trim().toLowerCase() ?? '') === modelFilter;
      return providerMatches && modelMatches;
    });
  }

  private buildProviderOptions(usage: LlmUsageDailyMetric[]): string[] {
    const providersFromUsage = Array.from(
      new Set(
        usage
          .filter((item) => this.hasValidUsageEvidence(item))
          .map((item) => item.provider?.trim())
          .filter((provider): provider is string => Boolean(provider))
      )
    );

    return providersFromUsage;
  }

  private buildModelOptions(usage: LlmUsageDailyMetric[]): string[] {
    const providerFilter = this.filterForm.provider.trim().toLowerCase();

    const modelsFromUsage = Array.from(
      new Set(
        usage
          .filter((item) => {
            if (!providerFilter) {
              return true;
            }

            return (item.provider?.trim().toLowerCase() ?? '') === providerFilter;
          })
          .filter((item) => this.hasValidUsageEvidence(item))
          .map((item) => item.model?.trim())
          .filter((model): model is string => Boolean(model))
      )
    );

    return modelsFromUsage;
  }

  getModelFilterPlaceholder(): string {
    if (this.modelOptions.length > 0) {
      return 'Todos los modelos';
    }

    if (this.hasUsageWithMissingModel()) {
      return 'Hay uso registrado, pero el backend no informa modelo para este filtro';
    }

    return 'Sin modelos con uso registrado para este filtro';
  }

  getProviderFilterPlaceholder(): string {
    if (this.providerOptions.length > 0) {
      return 'Todos los proveedores';
    }

    if (this.hasUsageWithMissingProvider()) {
      return 'Hay uso registrado, pero el backend no informa proveedor para este período';
    }

    return 'Sin proveedores con uso registrado en este período';
  }

  private hasValidUsageEvidence(item: LlmUsageDailyMetric): boolean {
    if (item.tokenEstimated === false) {
      return true;
    }

    const tokenSource = item.tokenSource?.trim().toLowerCase();
    if (tokenSource === 'real') {
      return true;
    }

    return this.hasUsageEvidence(item);
  }

  private hasUsageWithMissingModel(): boolean {
    return this.rawUsage.some((item) => this.hasUsageEvidence(item) && !item.model?.trim());
  }

  private hasUsageWithMissingProvider(): boolean {
    return this.rawUsage.some((item) => this.hasUsageEvidence(item) && !item.provider?.trim());
  }

  private hasUsageEvidence(item: LlmUsageDailyMetric): boolean {
    return item.calls > 0 || item.tokensInput > 0 || item.tokensOutput > 0;
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

  private buildClientSummary(usage: LlmUsageDailyMetric[], backendSummary: LlmUsageSummary): LlmUsageSummary {
    const totalCalls = usage.reduce((total, item) => total + item.calls, 0);
    const totalTokens = usage.reduce((total, item) => total + item.tokensInput + item.tokensOutput, 0);
    const totalLatencyWeight = usage.reduce((total, item) => total + (item.avgLatencyMs ?? 0) * item.calls, 0);
    const totalLatencyCalls = usage.reduce((total, item) => total + (item.avgLatencyMs !== null ? item.calls : 0), 0);
    const avgLatencyMs = totalLatencyCalls > 0 ? Math.round(totalLatencyWeight / totalLatencyCalls) : null;

    const fallbackCount = usage
      .filter((item) => item.status?.trim().toLowerCase() === 'fallback')
      .reduce((total, item) => total + item.calls, 0);
    const errorCount = usage
      .filter((item) => item.status?.trim().toLowerCase() === 'error')
      .reduce((total, item) => total + item.calls, 0);

    const hasModelOrProviderFilter = Boolean(this.filterForm.provider.trim() || this.filterForm.model.trim());
    const safeBackendTokens = backendSummary.totalTokens > 0 ? backendSummary.totalTokens : 0;
    const tokenRatio = safeBackendTokens > 0 ? Math.min(totalTokens / safeBackendTokens, 1) : 0;

    return {
      totalCalls,
      totalTokens,
      avgLatencyMs,
      fallbackCount,
      errorCount,
      estimatedCostUsd: hasModelOrProviderFilter ? backendSummary.estimatedCostUsd * tokenRatio : backendSummary.estimatedCostUsd,
      estimatedCostClp: hasModelOrProviderFilter ? backendSummary.estimatedCostClp * tokenRatio : backendSummary.estimatedCostClp,
      usdToClpRate: backendSummary.usdToClpRate
    };
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
