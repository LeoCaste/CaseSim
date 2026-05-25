import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserContext } from '../../../../core/services/user-context';
import { ClinicalCaseCard } from '../../components/clinical-case-card/clinical-case-card';
import { ClinicalCaseSummary } from '../../../../core/models/clinical-case.model';
import { ClinicalCaseService } from '../../../../core/services/clinical-case.service';
import { finalize, take } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-clinical-case-list-page',
  imports: [CommonModule, RouterLink, ClinicalCaseCard],
  templateUrl: './clinical-case-list-page.html',
  styleUrl: './clinical-case-list-page.css',
})
export class ClinicalCaseListPage implements OnInit {
  loading = false;
  error = '';
  success = '';
  cases: ClinicalCaseSummary[] = [];
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private userContext: UserContext,
    private clinicalCaseService: ClinicalCaseService,
    private cdr: ChangeDetectorRef
  ) {
    this.userContext.setRole('professor');
  }

  ngOnInit(): void {
    this.loadCases();
  }

  private loadCases(): void {
    this.loading = true;
    this.error = '';

    this.clinicalCaseService
      .getAll()
      .pipe(
        take(1),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (cases) => {
          this.cases = cases;
          this.cdr.detectChanges();
        },
        error: () => {
          this.cases = [];
          this.error = 'No fue posible cargar los casos clínicos.';
          this.cdr.detectChanges();
        }
      });
  }

  onCaseDeleted(caseId: string): void {
    this.cases = this.cases.filter((clinicalCase) => clinicalCase.id !== caseId);
    this.success = 'Caso clínico eliminado correctamente.';
    this.error = '';
    this.cdr.detectChanges();
  }
}
