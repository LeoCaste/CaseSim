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

  usage: LlmUsageDailyMetric[] = [];
  summary: LlmUsageSummary | null = null;
  isLoading = false;
  loadError = '';

  modelOptions = ['gpt-4o-mini', 'gpt-4.1-mini', 'claude-3-haiku'];
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
    this.loadSnapshot(this.activeFilters);
  }

  applyFilters(): void {
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

  hasModelColumn(): boolean {
    return this.usage.some((item) => Boolean((item as LlmUsageDailyMetric & { model?: string }).model));
  }

  hasStatusColumn(): boolean {
    return this.usage.some((item) => Boolean((item as LlmUsageDailyMetric & { status?: string }).status));
  }

  getModelValue(item: LlmUsageDailyMetric): string {
    return (item as LlmUsageDailyMetric & { model?: string }).model ?? '-';
  }

  getStatusValue(item: LlmUsageDailyMetric): string {
    return (item as LlmUsageDailyMetric & { status?: string }).status ?? '-';
  }

  getEstimatedCostUsd(): number {
    const value = this.summary?.estimatedCostUsd ?? 0;
    return Number.isFinite(value) ? value : 0;
  }

  getEstimatedCostClp(): number {
    const value = this.summary?.estimatedCostClp ?? 0;
    return Number.isFinite(value) ? value : 0;
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
          this.loadError = '';
          this.triggerViewUpdate();
        },
        error: () => {
          this.usage = [];
          this.summary = null;
          this.loadError = 'No fue posible cargar las métricas LLM.';
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

  private triggerViewUpdate(): void {
    if (this.destroyRef.destroyed) {
      return;
    }

    this.cdr.detectChanges();
  }
}
