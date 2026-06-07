import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SessionCompletedPage } from './session-completed-page';

describe('SessionCompletedPage', () => {
  let component: SessionCompletedPage;
  let fixture: ComponentFixture<SessionCompletedPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionCompletedPage],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionCompletedPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
