import { TestBed } from '@angular/core/testing';
import { GuardResult, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { firstValueFrom, of } from 'rxjs';
import { Observable } from 'rxjs';
import { Mock, vi } from 'vitest';

import { AuthService } from '../services/auth.service';
import { bootstrapFlowGuard } from './bootstrap-flow.guard';

describe('bootstrapFlowGuard', () => {
  let router: Router;
  let authServiceMock: { bootstrapStatus: Mock };

  beforeEach(() => {
    authServiceMock = {
      bootstrapStatus: vi.fn()
    };

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [{ provide: AuthService, useValue: authServiceMock }]
    });

    router = TestBed.inject(Router);
  });

  it('redirige a /setup cuando no existe admin inicial', async () => {
    authServiceMock.bootstrapStatus.mockReturnValue(of({ adminExists: false }));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => bootstrapFlowGuard({} as never, { url: '/login' } as never)) as Observable<GuardResult>
    );

    expect(router.serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/setup');
  });

  it('permite acceso normal cuando el admin inicial ya existe', async () => {
    authServiceMock.bootstrapStatus.mockReturnValue(of({ adminExists: true }));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => bootstrapFlowGuard({} as never, { url: '/login' } as never)) as Observable<GuardResult>
    );

    expect(result).toBe(true);
  });
});
