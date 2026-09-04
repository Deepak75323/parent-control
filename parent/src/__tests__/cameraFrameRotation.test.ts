import {cameraUprightRotateDeg} from '../utils/cameraFrameRotation';

describe('cameraUprightRotateDeg', () => {
  it('renders front camera upright at 0 when child sends CVO 0', () => {
    expect(cameraUprightRotateDeg(0)).toBe(0);
    expect(cameraUprightRotateDeg(270)).toBe(0);
  });

  it('applies manual viewer rotate only for front camera', () => {
    expect(cameraUprightRotateDeg(0, 90)).toBe(90);
  });

  it('passes through CVO for back camera', () => {
    expect(cameraUprightRotateDeg(180, 0, false)).toBe(180);
  });
});
