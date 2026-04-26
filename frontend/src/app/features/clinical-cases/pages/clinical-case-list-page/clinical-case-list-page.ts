import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCaseCard } from '../../components/clinical-case-card/clinical-case-card';
import { ClinicalCaseCardView, ClinicalCaseService } from '../../../../core/services/clinical-case.service';

@Component({
  selector: 'app-clinical-case-list-page',
  imports: [CommonModule, RouterLink, ClinicalCaseCard],
  templateUrl: './clinical-case-list-page.html',
  styleUrl: './clinical-case-list-page.css',
})
export class ClinicalCaseListPage implements OnInit {
  cases: ClinicalCaseCardView[] = [];
  isLoading = false;
  loadError = '';

  constructor(
    private userContext: UserContext,
    private clinicalCaseService: ClinicalCaseService
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.clinicalCaseService.getClinicalCaseCards().subscribe((cases) => {
      this.cases = cases;
      this.isLoading = false;
    });
  }
}
