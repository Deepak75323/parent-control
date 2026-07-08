import type {NavigationProp} from '@react-navigation/native';
import {childLoginPasswordApi} from '../services/api';
import type {RootStackParamList} from './RootNavigator';

type StackNav = NavigationProp<RootStackParamList>;

async function isFamilyPasswordConfigured(): Promise<boolean> {
  try {
    const {data} = await childLoginPasswordApi.status();
    return data.data?.configured ?? false;
  } catch {
    return false;
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
