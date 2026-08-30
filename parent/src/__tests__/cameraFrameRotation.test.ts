import {cameraUprightRotateDeg} from '../utils/cameraFrameRotation';

describe('cameraUprightRotateDeg', () => {
  it('applies no rotation by default because child frames arrive upright', () => {
    expect(cameraUprightRotateDeg()).toBe(0);
  });

  it('passes the viewer manual rotate through unchanged', () => {
    expect(cameraUprightRotateDeg(90)).toBe(90);
    expect(cameraUprightRotateDeg(180)).toBe(180);
    expect(cameraUprightRotateDeg(270)).toBe(270);
  });
});
