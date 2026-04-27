import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCaseCard } from '../../components/clinical-case-card/clinical-case-card';
import { ClinicalCaseSummary } from '../../../../core/models/clinical-case.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';

@Component({
  selector: 'app-clinical-case-list-page',
  imports: [CommonModule, RouterLink, ClinicalCaseCard],
  templateUrl: './clinical-case-list-page.html',
  styleUrl: './clinical-case-list-page.css',
})
export class ClinicalCaseListPage implements OnInit {
  loading = false;
  error = '';
  cases: ClinicalCaseSummary[] = [];

  constructor(
    private userContext: UserContext,
    private clinicalCaseService: ClinicalCaseService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.loading = true;
    this.error = '';

    this.clinicalCaseService.getAll().subscribe({
      next: (cases) => {
        this.cases = cases;
        this.loading = false;
      },
      error: () => {
        this.error = 'No fue posible cargar los casos clínicos.';
        this.loading = false;
      }
    });
  }
}
