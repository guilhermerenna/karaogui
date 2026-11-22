import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    delete window.playground;
    window.sessionStorage.removeItem('karaogui:playground');

    await TestBed.configureTestingModule({
      imports: [App],
      deferBlockBehavior: DeferBlockBehavior.Playthrough,
    }).compileComponents();
  });

  afterEach(() => {
    delete window.playground;
    window.sessionStorage.removeItem('karaogui:playground');
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
    fixture.destroy();
  });

  it('should render router-outlet by default and Playground when window.playground is true', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).toBeTruthy();
    expect(compiled.querySelector('app-playground')).toBeNull();

    window.playground = true;
    await fixture.whenStable();

    expect(compiled.querySelector('router-outlet')).toBeNull();
    expect(compiled.querySelector('app-playground')).toBeTruthy();
    fixture.destroy();
  });
});
