import {create} from 'zustand';
import {monitoringApi, parentPinApi} from '../api/services';
import type {StreamType} from '../types/api';
import {
  connectBrowserViewer,
  disconnectBrowserViewer,
  getActiveRoomId,
  setRemoteAudioEnabled,
} from '../realtime/webrtcViewer';
import type {ViewerRotateDeg} from '../lib/viewerVideoRotate';
import type {WebCameraFacing} from '../lib/cameraViewerLayout';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {
  largePipSize,
  pipSizeForAspect,
  pipVideoAspect,
} from './pipLayout';

export {PIP_SIZE_LIMITS, largePipSize} from './pipLayout';

type VideoFrame = {w: number; h: number};

interface LiveStreamState {
  deviceId: string | null;
  deviceName: string | null;
  sessionId: string | null;
  streamType: StreamType;
  status: string;
  error: string | null;
  busy: boolean;
  muted: boolean;
  viewRotate: ViewerRotateDeg;
  cameraFacing: WebCameraFacing;
  remoteStream: MediaStream | null;
  videoFrame: VideoFrame;
  pinNeeded: boolean;
  pin: string;
  pipMinimized: boolean;
  pipPosition: {x: number; y: number} | null;
  pipSize: {width: number; bodyHeight: number};
  hydratePinStatus: () => Promise<void>;
  setStreamType: (streamType: StreamType) => void;
  setPin: (pin: string) => void;
  setViewRotate: (deg: ViewerRotateDeg | ((prev: ViewerRotateDeg) => ViewerRotateDeg)) => void;
  setCameraFacing: (facing: WebCameraFacing | ((prev: WebCameraFacing) => WebCameraFacing)) => void;
  setVideoFrame: (frame: VideoFrame) => void;
  setPipMinimized: (minimized: boolean) => void;
  setPipPosition: (pos: {x: number; y: number} | null) => void;
  setPipSize: (size: {width: number; bodyHeight: number}) => void;
  resetView: () => void;
  expandPip: () => void;
  resetPipLayout: () => void;
  registerVideoFullscreenToggle: (toggle: (() => void) | null) => void;
  toggleVideoFullscreen: () => void;
  start: (deviceId: string, deviceName: string) => Promise<void>;
  stop: () => Promise<void>;
  flipCamera: () => Promise<void>;
  toggleMute: () => void;
  releaseFromTakeover: () => void;
  reset: () => void;
}

let roomIdRef: string | null = null;
let videoFullscreenToggle: (() => void) | null = null;

const initialState = {
  deviceId: null as string | null,
  deviceName: null as string | null,
  sessionId: null as string | null,
  streamType: 'CAMERA' as StreamType,
  status: 'Idle',
  error: null as string | null,
  busy: false,
  muted: false,
  viewRotate: 0 as ViewerRotateDeg,
  cameraFacing: 'user' as WebCameraFacing,
  remoteStream: null as MediaStream | null,
  videoFrame: {w: 0, h: 0},
  pinNeeded: false,
  pin: '',
  pipMinimized: false,
  pipPosition: null as {x: number; y: number} | null,
  pipSize: {width: 320, bodyHeight: 180},
};

export const useLiveStreamStore = create<LiveStreamState>((set, get) => ({
  ...initialState,

  hydratePinStatus: async () => {
    try {
      const {data} = await parentPinApi.status();
      set({pinNeeded: Boolean(data.data?.pinConfigured)});
    } catch {
      /* optional */
    }
  },

  setStreamType: streamType => set({streamType}),
  setPin: pin => set({pin}),
  setViewRotate: viewRotate =>
    set(state => ({
      viewRotate: typeof viewRotate === 'function' ? viewRotate(state.viewRotate) : viewRotate,
    })),
  setCameraFacing: cameraFacing =>
    set(state => ({
      cameraFacing: typeof cameraFacing === 'function' ? cameraFacing(state.cameraFacing) : cameraFacing,
    })),
  setVideoFrame: videoFrame => set({videoFrame}),
  setPipMinimized: pipMinimized => set({pipMinimized}),
  setPipPosition: pipPosition => set({pipPosition}),
  setPipSize: pipSize => set({pipSize}),
  resetView: () => set({viewRotate: 0}),
  expandPip: () => {
    const {videoFrame, viewRotate, streamType} = get();
    const aspect = pipVideoAspect(videoFrame, viewRotate, streamType);
    set({
      pipMinimized: false,
      pipSize: pipSizeForAspect(aspect, largePipSize()),
    });
  },
  resetPipLayout: () => {
    const {videoFrame, viewRotate, streamType} = get();
    const aspect = pipVideoAspect(videoFrame, viewRotate, streamType);
    set({
      pipMinimized: false,
      pipSize: pipSizeForAspect(aspect),
      viewRotate: 0,
    });
  },
  registerVideoFullscreenToggle: toggle => {
    videoFullscreenToggle = toggle;
  },
  toggleVideoFullscreen: () => {
    videoFullscreenToggle?.();
  },

  start: async (deviceId, deviceName) => {
    const {streamType, pinNeeded, pin, busy} = get();
    if (busy) {
      return;
    }
    set({
      busy: true,
      error: null,
      status: 'Starting…',
      deviceId,
      deviceName,
      viewRotate: 0,
      videoFrame: {w: 0, h: 0},
      remoteStream: null,
      cameraFacing: 'user',
      pipMinimized: false,
    });
    useRealtimeStore.getState().setStreamingState(undefined);
    try {
      await disconnectBrowserViewer();
      const {data} = await monitoringApi.startStream(
        deviceId,
        streamType,
        pinNeeded && streamType === 'SCREEN' && pin ? pin : undefined,
      );
      const session = data.data;
      if (!session?.signalingRoomId || !session.sessionId) {
        throw new Error(data.message ?? 'No stream session returned');
      }
      roomIdRef = session.signalingRoomId;
      set({
        sessionId: session.sessionId,
        status: `Connecting (${session.status})…`,
      });
      await connectBrowserViewer(session.signalingRoomId, session.sessionId, {
        onRemoteStream: stream => set({remoteStream: stream}),
        onConnectionState: s => set({status: `ICE: ${s}`}),
        onError: msg => set({error: msg}),
      });
      set({status: 'Live'});
    } catch (err: unknown) {
      let msg = 'Stream failed';
      if (err instanceof Error) {
        msg = err.message;
      }
      if (err && typeof err === 'object' && 'response' in err) {
        const response = (err as {response?: {data?: {message?: string}}}).response;
        if (response?.data?.message) {
          msg = response.data.message;
        }
      }
      set({
        error: msg,
        status: 'Failed',
        sessionId: null,
        deviceId: null,
        deviceName: null,
      });
      roomIdRef = null;
    } finally {
      set({busy: false});
    }
  },

  stop: async () => {
    const {sessionId, busy} = get();
    if (busy) {
      return;
    }
    set({busy: true});
    try {
      if (sessionId) {
        await monitoringApi.stopScreenShare(sessionId).catch(() => undefined);
      }
      await disconnectBrowserViewer();
      roomIdRef = null;
      set({
        ...initialState,
        pinNeeded: get().pinNeeded,
        streamType: get().streamType,
      });
    } finally {
      set({busy: false});
    }
  },

  flipCamera: async () => {
    const {sessionId} = get();
    if (!sessionId) {
      return;
    }
    try {
      await monitoringApi.switchCamera(sessionId);
      get().setCameraFacing(prev => (prev === 'user' ? 'environment' : 'user'));
      set({viewRotate: 0, videoFrame: {w: 0, h: 0}});
    } catch {
      set({error: 'Could not switch camera'});
    }
  },

  toggleMute: () => {
    const muted = !get().muted;
    set({muted});
    setRemoteAudioEnabled(!muted);
  },

  releaseFromTakeover: () => {
    void disconnectBrowserViewer();
    roomIdRef = null;
    set({
      sessionId: null,
      remoteStream: null,
      busy: false,
      viewRotate: 0,
      videoFrame: {w: 0, h: 0},
      status: 'Moved to another client',
      error: 'Live view continued on the parent app or another browser tab.',
    });
  },

  reset: () => {
    roomIdRef = null;
    set({...initialState, pinNeeded: get().pinNeeded});
  },
}));

export function getLiveStreamRoomId(): string | null {
  return roomIdRef ?? getActiveRoomId();
}

export function isLiveStreamActive(): boolean {
  return Boolean(useLiveStreamStore.getState().sessionId);
}
