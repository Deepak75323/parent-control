import {NativeModules, Platform} from 'react-native';

const Native = NativeModules.ParentNative ?? {};

/** True when GOOGLE_MAPS_API_KEY is set in android/local.properties (Android only). */
export const mapsAvailable: boolean =
  Platform.OS !== 'android' || Native.mapsAvailable === true;

export const nativeModules = {
  isAndroid: Platform.OS === 'android',
  setViewerAudioMode: (enabled: boolean): Promise<void> =>
    Native.setViewerAudioMode?.(enabled) ?? Promise.resolve(),
};
