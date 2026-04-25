import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CsCard } from './cs-card';

describe('CsCard', () => {
  let component: CsCard;
  let fixture: ComponentFixture<CsCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CsCard],
    }).compileComponents();

    fixture = TestBed.createComponent(CsCard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
