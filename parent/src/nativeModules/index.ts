import {NativeModules, Platform} from 'react-native';

const Native = NativeModules.ParentNative ?? {};

export type ParentNativeModule = {
  mapsAvailable?: boolean;
  setViewerAudioMode?: (enabled: boolean) => Promise<void>;
  isStreamRecordingSupported?: () => boolean;
  startStreamRecording?: (
    streamType: string,
    peerConnectionId: number,
    videoTrackId: string | null,
    includeRemoteAudio: boolean,
  ) => Promise<void>;
  stopStreamRecording?: (streamType: string) => Promise<string>;
};

/** True when GOOGLE_MAPS_API_KEY is set in android/local.properties (Android only). */
export const mapsAvailable: boolean =
  Platform.OS !== 'android' || Native.mapsAvailable === true;

export function getParentNative(): ParentNativeModule {
  return Native as ParentNativeModule;
}

export const nativeModules = {
  isAndroid: Platform.OS === 'android',
  setViewerAudioMode: (enabled: boolean): Promise<void> =>
    Native.setViewerAudioMode?.(enabled) ?? Promise.resolve(),
};
