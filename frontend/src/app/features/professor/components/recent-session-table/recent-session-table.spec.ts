import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecentSessionTable } from './recent-session-table';

describe('RecentSessionTable', () => {
  let component: RecentSessionTable;
  let fixture: ComponentFixture<RecentSessionTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecentSessionTable],
    }).compileComponents();

    fixture = TestBed.createComponent(RecentSessionTable);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
