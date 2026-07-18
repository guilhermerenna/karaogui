import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Realtime } from './realtime';

describe('Realtime', () => {
  let component: Realtime;
  let fixture: ComponentFixture<Realtime>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Realtime],
    }).compileComponents();

    fixture = TestBed.createComponent(Realtime);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
