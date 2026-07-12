import {Alert, Platform} from 'react-native';
import {getParentNative} from '../nativeModules';

export type RecordableStreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

export type VideoTrackRecordingInfo = {
  peerConnectionId: number;
  trackId: string;
};

export function isStreamRecordingSupported(): boolean {
  return Platform.OS === 'android' && Number(Platform.Version) >= 29;
}

export async function startStreamRecording(
  streamType: RecordableStreamType,
  videoTrack?: VideoTrackRecordingInfo | null,
  includeRemoteAudio = false,
): Promise<void> {
  if (!isStreamRecordingSupported()) {
    throw new Error('Recording requires Android 10 or newer');
  }
  const peerConnectionId = videoTrack?.peerConnectionId ?? -1;
  const videoTrackId = videoTrack?.trackId ?? null;
  await getParentNative().startStreamRecording!(
    streamType,
    peerConnectionId,
    videoTrackId,
    includeRemoteAudio,
  );
}

export async function stopStreamRecording(streamType: RecordableStreamType): Promise<string> {
  return getParentNative().stopStreamRecording!(streamType);
}

export function showRecordingSavedAlert(
  streamType: RecordableStreamType,
  options?: {includedRemoteAudio?: boolean},
): void {
  let format: string;
  if (streamType === 'AUDIO') {
    format = 'WAV audio';
  } else if (options?.includedRemoteAudio) {
    format = 'MP4 video with audio';
  } else {
    format = 'MP4 video';
  }
  Alert.alert(
    'Recording saved',
    `Saved as ${format} in Downloads/ParentalControl on this phone.`,
    [{text: 'OK'}],
  );
}
