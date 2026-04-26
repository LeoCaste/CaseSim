import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WaitingRoomPage } from './waiting-room-page';

describe('WaitingRoomPage', () => {
  let component: WaitingRoomPage;
  let fixture: ComponentFixture<WaitingRoomPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WaitingRoomPage],
    }).compileComponents();

    fixture = TestBed.createComponent(WaitingRoomPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
