import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';

import { LlmUsageDailyMetric, LlmUsageSummary } from '../../../../core/models/admin-llm.model';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-admin-llm-usage-page',
  imports: [CommonModule],
  templateUrl: './admin-llm-usage-page.html',
  styleUrl: './admin-llm-usage-page.css'
})
export class AdminLlmUsagePage implements OnInit {
  usage: LlmUsageDailyMetric[] = [];
  summary: LlmUsageSummary | null = null;
  isLoading = false;
  loadError = '';

  constructor(
    private adminLlmService: AdminLlmService,
    private userContext: UserContext
  ) {
    this.userContext.setRole('admin');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.loadError = '';

    forkJoin({
      usage: this.adminLlmService.getUsage(),
      summary: this.adminLlmService.getSummary()
    }).subscribe({
      next: ({ usage, summary }) => {
        this.usage = usage;
        this.summary = summary;
        this.isLoading = false;
      },
      error: () => {
        this.usage = [];
        this.summary = null;
        this.loadError = 'No fue posible cargar las métricas LLM.';
        this.isLoading = false;
      }
    });
  }
}
