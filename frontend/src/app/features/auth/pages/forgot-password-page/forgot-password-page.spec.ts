import { ComponentFixture, TestBed } from '@angular/core/testing';
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
});
