import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SessionTranscript } from './session-transcript';

describe('SessionTranscript', () => {
  let component: SessionTranscript;
  let fixture: ComponentFixture<SessionTranscript>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionTranscript],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionTranscript);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
