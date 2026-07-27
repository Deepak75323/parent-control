import {isSoftError, isSoftSuccess} from '../appAlert';

describe('appAlert soft heuristics', () => {
  it('treats Sent / command sent as toast success', () => {
    expect(isSoftSuccess('Sent', 'Stop ring command sent')).toBe(true);
    expect(isSoftSuccess('Success', 'Saved')).toBe(true);
  });

  it('keeps confirms as dialogs', () => {
    expect(isSoftSuccess('Ring device', 'Play a loud alarm?')).toBe(false);
    expect(isSoftSuccess('Stop ring?', 'Silence now?')).toBe(false);
  });

  it('treats Error title as soft error toast', () => {
    expect(isSoftError('Error')).toBe(true);
    expect(isSoftError('Password required')).toBe(false);
  });
});
