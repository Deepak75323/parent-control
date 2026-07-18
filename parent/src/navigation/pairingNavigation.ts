import type {NavigationProp} from '@react-navigation/native';
import {childLoginPasswordApi, familyHandleApi} from '../services/api';
import type {RootStackParamList} from './RootNavigator';

type StackNav = NavigationProp<RootStackParamList>;

async function isFamilyHandleConfigured(): Promise<boolean> {
  try {
    const {data} = await familyHandleApi.status();
    return data.data?.configured === true;
  } catch {
    return true;
  }
}

async function isFamilyPasswordConfigured(): Promise<boolean> {
  try {
    const {data} = await childLoginPasswordApi.status();
    const status = data.data;
    if (status?.configured) {
      return true;
    }
    if ((status?.linkedDeviceCount ?? 0) > 0) {
      return true;
    }
    return false;
  } catch {
    return true;
  }
}

/** Pair & manage — requires family handle, then child login password, then device list. */
export async function navigateToPairingFlow(navigation: StackNav): Promise<void> {
  const handleOk = await isFamilyHandleConfigured();
  if (!handleOk) {
    navigation.navigate('FamilyHandle', {afterSetup: 'pairing'});
    return;
  }
  const passwordOk = await isFamilyPasswordConfigured();
  if (!passwordOk) {
    navigation.navigate('ChildLoginPassword', {afterSetup: 'DeviceList'});
    return;
  }
  navigation.navigate('DeviceList');
}

export async function resolvePairingDestination(): Promise<{
  name: 'DeviceList' | 'ChildLoginPassword' | 'FamilyHandle';
  params?: RootStackParamList['ChildLoginPassword'] | RootStackParamList['FamilyHandle'];
}> {
  const handleOk = await isFamilyHandleConfigured();
  if (!handleOk) {
    return {name: 'FamilyHandle', params: {afterSetup: 'pairing'}};
  }
  const passwordOk = await isFamilyPasswordConfigured();
  if (!passwordOk) {
    return {name: 'ChildLoginPassword', params: {afterSetup: 'DeviceList'}};
  }
  return {name: 'DeviceList'};
}
