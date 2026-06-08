import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Mock, vi } from 'vitest';

import { SetupPage } from './setup-page';
import { AuthService } from '../../../../core/services/auth.service';

describe('SetupPage', () => {
  let component: SetupPage;
  let fixture: ComponentFixture<SetupPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SetupPage],
      providers: [
        {
          provide: AuthService,
          useValue: {
            bootstrapAdmin: vi.fn().mockReturnValue(of(void 0))
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

    fixture = TestBed.createComponent(SetupPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should submit the first admin payload and redirect to login', () => {
    const authService = TestBed.inject(AuthService) as unknown as { bootstrapAdmin: Mock };
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');

    component.email = 'Admin@CaseSim.cl';
    component.password = 'Password1';
    component.confirmPassword = 'Password1';

    component.submit();

    expect(authService.bootstrapAdmin).toHaveBeenCalledWith({
      email: 'admin@casesim.cl',
      password: 'Password1',
      confirmPassword: 'Password1'
    });
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    expect(component.isSubmitting).toBe(false);
  });

  it('should show backend errors and unlock the form', () => {
    const authService = TestBed.inject(AuthService) as unknown as { bootstrapAdmin: Mock };
    authService.bootstrapAdmin.mockReturnValue(throwError(() => new Error('network')));

    component.email = 'admin@casesim.cl';
    component.password = 'Password1';
    component.confirmPassword = 'Password1';

    component.submit();

    expect(component.errorMessage).toContain('No fue posible crear el primer administrador');
    expect(component.isSubmitting).toBe(false);
  });
});
