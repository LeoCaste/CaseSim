import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { firstValueFrom, of, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { setupAccessGuard } from './setup-access.guard';

describe('setupAccessGuard', () => {
  let router: Router;
  let authServiceMock: { bootstrapStatus: jasmine.Spy };

  beforeEach(() => {
    authServiceMock = {
      bootstrapStatus: jasmine.createSpy('bootstrapStatus')
    };

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [{ provide: AuthService, useValue: authServiceMock }]
    });

    router = TestBed.inject(Router);
  });

  it('permite acceso cuando necesita setup inicial', async () => {
    authServiceMock.bootstrapStatus.and.returnValue(of({ needsInitialSetup: true }));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => setupAccessGuard({} as never, {} as never))
    );

    expect(result).toBeTrue();
  });

  it('redirige a /login cuando no necesita setup inicial', async () => {
    authServiceMock.bootstrapStatus.and.returnValue(of({ needsInitialSetup: false }));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => setupAccessGuard({} as never, {} as never))
    );

    expect(router.serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/login');
  });

  it('redirige a /login cuando bootstrap-status falla', async () => {
    authServiceMock.bootstrapStatus.and.returnValue(throwError(() => new Error('network')));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => setupAccessGuard({} as never, {} as never))
    );

    expect(router.serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/login');
  });
});
