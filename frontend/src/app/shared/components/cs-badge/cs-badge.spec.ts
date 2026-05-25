import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CsBadge } from './cs-badge';

describe('CsBadge', () => {
  let component: CsBadge;
  let fixture: ComponentFixture<CsBadge>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CsBadge],
    }).compileComponents();

    fixture = TestBed.createComponent(CsBadge);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
