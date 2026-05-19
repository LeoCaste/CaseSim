import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

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
            bootstrapAdmin: () => of(void 0)
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
});
