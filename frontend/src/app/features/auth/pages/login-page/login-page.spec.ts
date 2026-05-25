import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { LoginPage } from './login-page';
import { AuthService } from '../../../../core/services/auth.service';
import { UserContext } from '../../../../core/services/user-context';

describe('LoginPage', () => {
  let component: LoginPage;
  let fixture: ComponentFixture<LoginPage>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const buildUnauthorizedError = () => new HttpErrorResponse({ status: 401 });

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['preCheck', 'login']);
    authServiceSpy.preCheck.and.returnValue(of({ requiresPassword: false }));
    authServiceSpy.login.and.returnValue(
      of({ id: 'user-1', fullName: 'Usuario Test', email: 'test@casesim.cl', role: 'student' })
    );

    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: UserContext,
          useValue: {
            setUser: jasmine.createSpy('setUser')
          }
        }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(LoginPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should hide forgot-password link by default', () => {
    component.requiresPassword = true;
    component.email = 'admin@casesim.cl';
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[routerLink="/forgot-password"]');
    expect(link).toBeNull();
  });

  it('should show forgot-password link only after invalid admin password', () => {
    component.email = 'admin@casesim.cl';
    component.requiresPassword = true;
    component.password = 'incorrecta';
    (component as any).passwordRequiredForEmail = 'admin@casesim.cl';
    authServiceSpy.login.and.returnValue(throwError(() => buildUnauthorizedError()));

    component.login();
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[routerLink="/forgot-password"]');
    expect(component.showForgotPasswordLink).toBeTrue();
    expect(link).not.toBeNull();
  });

  it('should trigger change detection immediately on invalid admin password', () => {
    component.email = 'admin@casesim.cl';
    component.requiresPassword = true;
    component.password = 'incorrecta';
    (component as any).passwordRequiredForEmail = 'admin@casesim.cl';
    authServiceSpy.login.and.returnValue(throwError(() => buildUnauthorizedError()));
    const detectChangesSpy = spyOn((component as any).cdr, 'detectChanges').and.callThrough();

    component.login();

    expect(component.showForgotPasswordLink).toBeTrue();
    expect(component.errorMessage).toBe('Contraseña inválida.');
    expect(detectChangesSpy).toHaveBeenCalled();
  });

  it('should keep forgot-password link hidden for non-admin unauthorized login', () => {
    component.email = 'estudiante@casesim.cl';
    component.requiresPassword = false;
    component.password = '';
    authServiceSpy.preCheck.and.returnValue(of({ requiresPassword: false }));
    authServiceSpy.login.and.returnValue(throwError(() => buildUnauthorizedError()));

    component.login();
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[routerLink="/forgot-password"]');
    expect(component.showForgotPasswordLink).toBeFalse();
    expect(link).toBeNull();
  });
});
