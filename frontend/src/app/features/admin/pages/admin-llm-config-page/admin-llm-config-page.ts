import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { LlmConfig, LlmTestConnectionResult, UpdateLlmConfigPayload } from '../../../../core/models/admin-llm.model';
import { AdminLlmService } from '../../../../core/services/admin-llm.service';
import { UserContext } from '../../../../core/services/user-context';

@Component({
  selector: 'app-admin-llm-config-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-llm-config-page.html',
  styleUrl: './admin-llm-config-page.css'
})
export class AdminLlmConfigPage implements OnInit {
  config: LlmConfig | null = null;
  form: UpdateLlmConfigPayload = {
    provider: '',
    model: '',
    baseUrl: '',
    enabled: true,
    apiKey: ''
  };

  isLoading = false;
  isSaving = false;
  isTestingConnection = false;
  loadError = '';
  saveMessage = '';
  saveError = '';
  testResult: LlmTestConnectionResult | null = null;

  constructor(
    private adminLlmService: AdminLlmService,
    private userContext: UserContext
  ) {
    this.userContext.setRole('admin');
  }

  ngOnInit(): void {
    this.loadConfig();
  }

  onSave(): void {
    this.saveMessage = '';
    this.saveError = '';

    if (!this.form.provider.trim() || !this.form.model.trim() || !this.form.baseUrl.trim()) {
      this.saveError = 'Completa proveedor, modelo y URL base para guardar la configuración.';
      return;
    }

    this.isSaving = true;
    this.adminLlmService.updateConfig(this.form).subscribe({
      next: (updatedConfig) => {
        this.config = updatedConfig;
        this.form = {
          provider: updatedConfig.provider,
          model: updatedConfig.model,
          baseUrl: updatedConfig.baseUrl,
          enabled: updatedConfig.enabled,
          apiKey: ''
        };
        this.saveMessage = 'Configuración guardada correctamente.';
        this.isSaving = false;
      },
      error: () => {
        this.saveError = 'No fue posible actualizar la configuración LLM.';
        this.isSaving = false;
      }
    });
  }

  onTestConnection(): void {
    this.testResult = null;
    this.isTestingConnection = true;

    this.adminLlmService.testConnection().subscribe({
      next: (response) => {
        this.testResult = response;
        this.isTestingConnection = false;
      },
      error: () => {
        this.testResult = {
          success: false,
          message: 'No fue posible ejecutar la prueba de conexión.'
        };
        this.isTestingConnection = false;
      }
    });
  }

  private loadConfig(): void {
    this.isLoading = true;
    this.loadError = '';

    this.adminLlmService.getConfig().subscribe({
      next: (config) => {
        this.config = config;
        this.form = {
          provider: config.provider,
          model: config.model,
          baseUrl: config.baseUrl,
          enabled: config.enabled,
          apiKey: ''
        };
        this.isLoading = false;
      },
      error: () => {
        this.config = null;
        this.loadError = 'No fue posible cargar la configuración actual.';
        this.isLoading = false;
      }
    });
  }
}
