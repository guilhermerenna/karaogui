import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { usePlaygroundMode } from './playground-mode';

@Component({
  selector: 'lib-test-host',
  template: `{{ playground() }}`,
})
class TestHostComponent {
  readonly playground = usePlaygroundMode();
}

describe('usePlaygroundMode', () => {
  beforeEach(() => {
    delete window.playground;
    window.sessionStorage.removeItem('karaogui:playground');
  });

  afterEach(() => {
    delete window.playground;
    window.sessionStorage.removeItem('karaogui:playground');
  });

  it('defaults to false when window.playground is unset', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    expect(fixture.componentInstance.playground()).toBe(false);
    expect(window.playground).toBe(false);
    fixture.destroy();
  });

  it('activates immediately when window.playground is true before creation', () => {
    window.playground = true;

    const fixture = TestBed.createComponent(TestHostComponent);
    expect(fixture.componentInstance.playground()).toBe(true);
    expect(window.sessionStorage.getItem('karaogui:playground')).toBeNull();
    fixture.destroy();
  });

  it('reactively toggles and persists in sessionStorage when assigned at runtime', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    expect(fixture.componentInstance.playground()).toBe(false);

    window.playground = true;
    expect(fixture.componentInstance.playground()).toBe(true);
    expect(window.sessionStorage.getItem('karaogui:playground')).toBe('true');

    window.playground = false;
    expect(fixture.componentInstance.playground()).toBe(false);
    expect(window.sessionStorage.getItem('karaogui:playground')).toBeNull();

    fixture.destroy();
  });

  it('restores playground mode from sessionStorage on reload unless explicitly overridden', () => {
    window.sessionStorage.setItem('karaogui:playground', 'true');

    const fixture1 = TestBed.createComponent(TestHostComponent);
    expect(fixture1.componentInstance.playground()).toBe(true);
    fixture1.destroy();

    window.playground = false;
    const fixture2 = TestBed.createComponent(TestHostComponent);
    expect(fixture2.componentInstance.playground()).toBe(false);
    fixture2.destroy();
  });
});
