import {describe, expect, it} from 'vitest';
import {fitChatMediaDimensions} from './chatMediaLayout';

describe('fitChatMediaDimensions', () => {
  it('keeps small images at natural size', () => {
    expect(fitChatMediaDimensions(200, 150)).toEqual({width: 200, height: 150});
  });

  it('scales wide landscape images to max width', () => {
    expect(fitChatMediaDimensions(840, 420)).toEqual({width: 420, height: 210});
  });

  it('scales tall portrait images to max height', () => {
    expect(fitChatMediaDimensions(300, 960)).toEqual({width: 100, height: 320});
  });

  it('falls back when dimensions are invalid', () => {
    expect(fitChatMediaDimensions(0, 0)).toEqual({width: 420, height: 320});
  });
});
