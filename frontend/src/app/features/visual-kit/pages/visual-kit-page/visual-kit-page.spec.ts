import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VisualKitPage } from './visual-kit-page';

describe('VisualKitPage', () => {
  let component: VisualKitPage;
  let fixture: ComponentFixture<VisualKitPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VisualKitPage],
    }).compileComponents();

    fixture = TestBed.createComponent(VisualKitPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
