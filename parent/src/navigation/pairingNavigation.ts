import type {NavigationProp} from '@react-navigation/native';
import {childLoginPasswordApi} from '../services/api';
import type {RootStackParamList} from './RootNavigator';

type StackNav = NavigationProp<RootStackParamList>;

async function isFamilyPasswordConfigured(): Promise<boolean> {
  try {
    const {data} = await childLoginPasswordApi.status();
    const status = data.data;
    if (status?.configured) {
      return true;
    }
    // Already paired at least one device — family password was set during first pairing.
    if ((status?.linkedDeviceCount ?? 0) > 0) {
      return true;
    }
    return false;
  } catch {
    // Network or server error — assume configured so we don't block the parent.
    return true;
  }
}

/** Pair & manage — requires family child login password to be set once first. */
export async function navigateToPairingFlow(navigation: StackNav): Promise<void> {
  const configured = await isFamilyPasswordConfigured();
  if (configured) {
    navigation.navigate('DeviceList');
  } else {
    navigation.navigate('ChildLoginPassword', {afterSetup: 'DeviceList'});
  }
}

export async function resolvePairingDestination(): Promise<{
  name: 'DeviceList' | 'ChildLoginPassword';
  params?: RootStackParamList['ChildLoginPassword'];
}> {
  const configured = await isFamilyPasswordConfigured();
  if (configured) {
    return {name: 'DeviceList'};
  }
  return {name: 'ChildLoginPassword', params: {afterSetup: 'DeviceList'}};
}
