import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { ResetPasswordPage } from './reset-password-page';
import { AuthService } from '../../../../core/services/auth.service';

describe('ResetPasswordPage', () => {
  let component: ResetPasswordPage;
  let fixture: ComponentFixture<ResetPasswordPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResetPasswordPage],
      providers: [
        {
          provide: AuthService,
          useValue: {
            resetPassword: () => of(void 0)
          }
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: () => 'token-example'
              }
            }
          }
        },
        {
          provide: Router,
          useValue: {
            navigate: () => Promise.resolve(true)
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
