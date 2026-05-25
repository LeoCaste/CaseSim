import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CsButton } from './cs-button';

describe('CsButton', () => {
  let component: CsButton;
  let fixture: ComponentFixture<CsButton>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CsButton],
    }).compileComponents();

    fixture = TestBed.createComponent(CsButton);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
