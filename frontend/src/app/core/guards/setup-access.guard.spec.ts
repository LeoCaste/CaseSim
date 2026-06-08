import { TestBed } from '@angular/core/testing';
import { GuardResult, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { Mock, vi } from 'vitest';
import { firstValueFrom, of, throwError } from 'rxjs';
import { Observable } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { setupAccessGuard } from './setup-access.guard';

describe('setupAccessGuard', () => {
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

  it('permite acceso cuando necesita setup inicial', async () => {
    authServiceMock.bootstrapStatus.mockReturnValue(of({ adminExists: false }));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => setupAccessGuard({} as never, {} as never)) as Observable<GuardResult>
    );

    expect(result).toBe(true);
  });

  it('redirige a /login cuando no necesita setup inicial', async () => {
    authServiceMock.bootstrapStatus.mockReturnValue(of({ adminExists: true }));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => setupAccessGuard({} as never, {} as never)) as Observable<GuardResult>
    );

    expect(router.serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/login');
  });

  it('redirige a /login cuando bootstrap status falla', async () => {
    authServiceMock.bootstrapStatus.mockReturnValue(throwError(() => new Error('network')));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => setupAccessGuard({} as never, {} as never)) as Observable<GuardResult>
    );

    expect(router.serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/login');
  });
});
