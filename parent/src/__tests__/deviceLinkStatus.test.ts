import {
  countActiveDevices,
  isRevokedStatus,
  partitionDevicesByLinkStatus,
} from '../utils/deviceLinkStatus';

describe('deviceLinkStatus', () => {
  const devices = [
    {id: 'a', status: 'ONLINE', deviceName: 'Phone A'},
    {id: 'b', status: 'OFFLINE', deviceName: 'Phone B'},
    {id: 'c', status: 'REVOKED', deviceName: 'Phone C'},
  ];

  it('isRevokedStatus matches REVOKED case-insensitively', () => {
    expect(isRevokedStatus('REVOKED')).toBe(true);
    expect(isRevokedStatus('revoked')).toBe(true);
    expect(isRevokedStatus('ONLINE')).toBe(false);
    expect(isRevokedStatus(undefined)).toBe(false);
  });

  it('partitionDevicesByLinkStatus puts revoked under previouslyLinked', () => {
    const {active, previouslyLinked} = partitionDevicesByLinkStatus(devices);
    expect(active.map(d => d.id)).toEqual(['a', 'b']);
    expect(previouslyLinked.map(d => d.id)).toEqual(['c']);
  });

  it('partitionDevicesByLinkStatus prefers live status for revoke', () => {
    const {active, previouslyLinked} = partitionDevicesByLinkStatus(devices, {
      a: {status: 'REVOKED'},
    });
    expect(active.map(d => d.id)).toEqual(['b']);
    expect(previouslyLinked.map(d => d.id)).toEqual(['a', 'c']);
  });

  it('countActiveDevices ignores revoked', () => {
    expect(countActiveDevices(devices as never)).toBe(2);
  });
});
