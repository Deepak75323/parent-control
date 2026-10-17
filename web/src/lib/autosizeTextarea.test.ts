import {describe, expect, it, vi} from 'vitest';
import {autosizeTextarea} from './autosizeTextarea';

function fakeTextarea(scrollHeight: number): HTMLTextAreaElement {
  const el = {
    style: {height: '', overflowY: ''} as CSSStyleDeclaration,
    scrollHeight,
  };
  return el as HTMLTextAreaElement;
}

describe('autosizeTextarea', () => {
  it('sets height to scrollHeight when under the max', () => {
    const el = fakeTextarea(72);
    autosizeTextarea(el, 144);
    expect(el.style.height).toBe('72px');
    expect(el.style.overflowY).toBe('hidden');
  });

  it('caps height and enables scroll when content exceeds max', () => {
    const el = fakeTextarea(220);
    autosizeTextarea(el, 144);
    expect(el.style.height).toBe('144px');
    expect(el.style.overflowY).toBe('auto');
  });

  it('resets height to auto before measuring', () => {
    const el = fakeTextarea(48);
    const setter = vi.fn();
    Object.defineProperty(el.style, 'height', {
      set: setter,
      get: () => '',
      configurable: true,
    });
    autosizeTextarea(el, 144);
    expect(setter).toHaveBeenCalledWith('auto');
    expect(setter).toHaveBeenCalledWith('48px');
  });
});
