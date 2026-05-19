import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ForgotPasswordPage } from './forgot-password-page';
import { AuthService } from '../../../../core/services/auth.service';

describe('ForgotPasswordPage', () => {
  let component: ForgotPasswordPage;
  let fixture: ComponentFixture<ForgotPasswordPage>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['forgotPassword']);
    authServiceSpy.forgotPassword.and.returnValue(of({}));

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
          useValue: authServiceSpy
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
          useValue: authServiceSpy
        }
      ]
    }).compileComponents();

    const prefilledFixture = TestBed.createComponent(ForgotPasswordPage);
    const prefilledComponent = prefilledFixture.componentInstance;

    expect(prefilledComponent.email).toBe('admin@casesim.cl');
  });

  it('should show backend message when payload includes message', () => {
    authServiceSpy.forgotPassword.and.returnValue(
      of({ message: 'La recuperación por correo no está habilitada. Contacte al administrador técnico.' })
    );
    component.email = 'admin@casesim.cl';

    component.submit();

    expect(component.infoMessage).toBe('La recuperación por correo no está habilitada. Contacte al administrador técnico.');
    expect(component.errorMessage).toBe('');
    expect(component.isSubmitting).toBeFalse();
  });

  it('should use neutral fallback message when backend message is not present', () => {
    authServiceSpy.forgotPassword.and.returnValue(of({}));
    component.email = 'admin@casesim.cl';

    component.submit();

    expect(component.infoMessage).toBe(
      'Si el correo existe en la plataforma, recibirás instrucciones para restablecer tu contraseña.'
    );
    expect(component.errorMessage).toBe('');
    expect(component.isSubmitting).toBeFalse();
  });

  it('should show error message and stop loading on request failure', () => {
    authServiceSpy.forgotPassword.and.returnValue(throwError(() => new Error('network error')));
    component.email = 'admin@casesim.cl';

    component.submit();

    expect(component.errorMessage).toBe('No fue posible procesar la solicitud. Intenta nuevamente.');
    expect(component.infoMessage).toBe('');
    expect(component.isSubmitting).toBeFalse();
  });
});
