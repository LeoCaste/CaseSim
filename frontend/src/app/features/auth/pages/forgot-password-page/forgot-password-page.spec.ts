import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ForgotPasswordPage } from './forgot-password-page';
import { AuthService } from '../../../../core/services/auth.service';

describe('ForgotPasswordPage', () => {
  let component: ForgotPasswordPage;
  let fixture: ComponentFixture<ForgotPasswordPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPasswordPage],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({})
            }
          }
        },
        {
          provide: AuthService,
          useValue: {
            forgotPassword: () => of(void 0)
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should prefill email from query param when present', async () => {
    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ForgotPasswordPage],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ email: 'ADMIN@CaseSim.cl ' })
            }
          }
        },
        {
          provide: AuthService,
          useValue: {
            forgotPassword: () => of(void 0)
          }
        }
      ]
    }).compileComponents();

    const prefilledFixture = TestBed.createComponent(ForgotPasswordPage);
    const prefilledComponent = prefilledFixture.componentInstance;

    expect(prefilledComponent.email).toBe('admin@casesim.cl');
  });
});
