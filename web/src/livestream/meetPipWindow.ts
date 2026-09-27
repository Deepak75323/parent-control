import {createElement} from 'react';
import {createRoot, type Root} from 'react-dom/client';
import {MEET_PIP_OPEN_SIZE, shouldReleaseMeetPip} from './browserPip';
import {isChatOnlyPipRequested, rememberChatRoom, setChatOnlyPip} from './chatPipTarget';
import {useLiveStreamStore} from './liveStreamStore';
import {MeetStylePip} from './MeetStylePip';

type DocumentPipApi = {
  requestWindow(options?: {width?: number; height?: number}): Promise<Window>;
};

let pipWindow: Window | null = null;
let pipRoot: Root | null = null;
let opening: Promise<boolean> | null = null;
let openGeneration = 0;

export function isChatPipWithoutCamera(): boolean {
  return isChatOnlyPipRequested() && isMeetPipOpen();
}

/** Float the parent notebook even when no child camera is running. */
export async function openChatPip(roomId?: string): Promise<boolean> {
  rememberChatRoom(roomId ?? null);
  setChatOnlyPip(true);
  const opened = await openMeetStylePip();
  if (!opened) {
    setChatOnlyPip(false);
  }
  return opened;
}

export function documentPipApi(): DocumentPipApi | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const api = (window as Window & {documentPictureInPicture?: DocumentPipApi}).documentPictureInPicture;
  return api ?? null;
}

export function isMeetPipOpen(): boolean {
  return pipWindow != null && !pipWindow.closed;
}

export async function openMeetStylePip(): Promise<boolean> {
  if (isMeetPipOpen()) {
    useLiveStreamStore.getState().setBrowserPipActive(true);
    return true;
  }
  if (opening) {
    return opening;
  }
  const generation = openGeneration;
  opening = openMeetStyleWindow(generation).finally(() => {
    opening = null;
  });
  return opening;
}

async function openMeetStyleWindow(generation: number): Promise<boolean> {
  const api = documentPipApi();
  if (!api) {
    return false;
  }
  const pip = await api.requestWindow(MEET_PIP_OPEN_SIZE);
  if (generation !== openGeneration || pip.closed) {
    if (!pip.closed) {
      pip.close();
    }
    return false;
  }
  const doc = pip.document;
  doc.title = 'Live view';
  doc.documentElement.style.height = '100%';
  doc.body.style.margin = '0';
  doc.body.style.height = '100%';
  doc.body.style.background = '#202124';
  const mount = doc.createElement('div');
  mount.id = 'meet-pip-root';
  mount.style.height = '100%';
  doc.body.appendChild(mount);
  pipWindow = pip;
  pipRoot = createRoot(mount);
  pipRoot.render(createElement(MeetStylePip, {onClose: closeMeetStylePip}));
  pip.addEventListener('pagehide', () => {
    window.setTimeout(() => {
      if (pipWindow !== pip || !shouldReleaseMeetPip(pip.closed)) {
        return;
      }
      const root = pipRoot;
      pipWindow = null;
      pipRoot = null;
      setChatOnlyPip(false);
      window.setTimeout(() => root?.unmount(), 0);
      useLiveStreamStore.getState().setBrowserPipActive(false);
    }, 0);
  });
  useLiveStreamStore.getState().setBrowserPipActive(true);
  return true;
}

export function closeMeetStylePip() {
  openGeneration += 1;
  setChatOnlyPip(false);
  const win = pipWindow;
  const root = pipRoot;
  if (!win && !root) {
    return;
  }
  pipWindow = null;
  pipRoot = null;
  window.setTimeout(() => {
    root?.unmount();
    if (win && !win.closed) {
      win.close();
    }
  }, 0);
  useLiveStreamStore.getState().setBrowserPipActive(false);
}
