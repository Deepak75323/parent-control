import {describe, expect, it} from 'vitest';
import {shouldCuePictureInPicture} from './pipAudible';
import {isBrowserCaptureActive, startBrowserCaptureForPip} from './pipBrowserCapture';
import {
  chatRoomForPip,
  hrefLeavesLiveDevice,
  hrefOpensParentChat,
  isChildVideoSession,
  isPaddedChatBody,
  shouldReleaseMeetPip,
  shouldReleaseMeetPipForCamera,
  mergeChatMessages,
  pathnameFromAppHref,
  isServerChatRoom,
  cameraPaneHeight,
  defaultCameraPane,
  meetPipWindowSize,
  pipMessageActionIds,
  pickLatestChatRoom,
  pipChatChoices,
  pipLockedText,
} from './browserPip';

describe('isChildVideoSession', () => {
  it('treats a live camera or screen session as video', () => {
    expect(isChildVideoSession('session-1', 'CAMERA')).toBe(true);
    expect(isChildVideoSession('session-1', 'SCREEN')).toBe(true);
  });

  it('ignores audio-only and idle sessions', () => {
    expect(isChildVideoSession('session-1', 'AUDIO')).toBe(false);
    expect(isChildVideoSession(null, 'CAMERA')).toBe(false);
  });
});

describe('pickLatestChatRoom', () => {
  it('chooses the notebook updated most recently', () => {
    const latest = pickLatestChatRoom([
      {id: 'old', updatedAt: '2026-01-01T00:00:00Z'},
      {id: 'new', updatedAt: '2026-09-01T00:00:00Z'},
    ]);
    expect(latest?.id).toBe('new');
    expect(pickLatestChatRoom([])).toBeNull();
  });
});

describe('pip chat rooms', () => {
  it('treats a named notebook as a server and a friend thread as direct', () => {
    expect(isServerChatRoom({directMessage: false, memberCount: 2})).toBe(true);
    expect(isServerChatRoom({directMessage: true, memberCount: 2})).toBe(false);
  });

  it('splits servers from direct chats so the floating window can open either', () => {
    const rooms = pipChatChoices([
      {id: 'server', directMessage: false, memberCount: 3},
      {id: 'friend', directMessage: true, memberCount: 2},
    ]);
    expect(rooms.servers.map(room => room.id)).toEqual(['server']);
    expect(rooms.directs.map(room => room.id)).toEqual(['friend']);
  });
});

describe('meetPipWindowSize', () => {
  it('shrinks the window to the live bar and restores the full size', () => {
    expect(meetPipWindowSize(false)).toEqual({width: 380, height: 640});
    expect(meetPipWindowSize(true)).toEqual({width: 360, height: 72});
    expect(meetPipWindowSize(true, false)).toEqual({width: 360, height: 72});
  });

  it('follows the Notes banner and keeps room for the chat', () => {
    expect(cameraPaneHeight(400, 100, 640)).toBe(300);
    expect(cameraPaneHeight(0, 0, 640)).toBe(96);
    expect(cameraPaneHeight(900, 0, 640)).toBe(420);
    expect(cameraPaneHeight(50, 0, 180)).toBe(96);
  });

  it('opens the camera at the large full-width size', () => {
    expect(defaultCameraPane(380, 640)).toEqual({w: 356, h: 420});
  });
});

describe('chat picture in picture without a camera', () => {
  it('keeps a requested chat window when no child camera is running', () => {
    expect(shouldReleaseMeetPipForCamera(false, true)).toBe(false);
    expect(shouldReleaseMeetPipForCamera(false, false)).toBe(true);
    expect(shouldReleaseMeetPipForCamera(true, false)).toBe(false);
  });

  it('opens the notebook the parent is already reading', () => {
    const rooms = [{id: 'server'}, {id: 'friend'}];
    expect(chatRoomForPip(rooms, 'friend', rooms[0])?.id).toBe('friend');
    expect(chatRoomForPip(rooms, null, rooms[0])?.id).toBe('server');
    expect(chatRoomForPip(rooms, 'missing', rooms[0])?.id).toBe('server');
  });
});

describe('pipMessageActionIds', () => {
  it('offers the same note actions as Notes for someone else', () => {
    expect(pipMessageActionIds({ownMessage: false, messageType: 'TEXT'})).toEqual([
      'react',
      'reply',
      'mark-unread',
      'delete-me',
      'copy',
    ]);
  });

  it('offers edit and delete for everyone on your own note', () => {
    expect(pipMessageActionIds({ownMessage: true, messageType: 'TEXT'})).toEqual([
      'react',
      'reply',
      'mark-unread',
      'edit',
      'delete-me',
      'delete-everyone',
    ]);
  });

  it('hides actions on a deleted note', () => {
    expect(pipMessageActionIds({deleted: true, ownMessage: true})).toEqual([]);
  });
});

describe('mergeChatMessages', () => {
  it('keeps chronological order and replaces a message by id', () => {
    const merged = mergeChatMessages(
      [
        {id: 'a', createdAt: '2026-01-01T00:00:00Z', body: 'one'},
        {id: 'b', createdAt: '2026-01-01T00:01:00Z', body: 'two'},
      ],
      [{id: 'a', createdAt: '2026-01-01T00:00:00Z', body: 'edited'}],
    );
    expect(merged.map(message => message.body)).toEqual(['edited', 'two']);
  });
});
describe('hrefOpensParentChat', () => {
  it('recognizes parent notes links with and without the portal base', () => {
    expect(hrefOpensParentChat('/chat')).toBe(true);
    expect(hrefOpensParentChat('/portal/chat')).toBe(true);
    expect(hrefOpensParentChat('/portal/chat/')).toBe(true);
  });

  it('ignores device, parents, and non-navigation links', () => {
    expect(hrefOpensParentChat('/portal/devices/abc')).toBe(false);
    expect(hrefOpensParentChat('/parents')).toBe(false);
    expect(hrefOpensParentChat('#chat')).toBe(false);
    expect(pathnameFromAppHref('mailto:parent@example.com')).toBeNull();
  });
});

describe('hrefLeavesLiveDevice', () => {
  it('stays on the same device page', () => {
    expect(hrefLeavesLiveDevice('/portal/devices/abc', 'abc')).toBe(false);
    expect(hrefLeavesLiveDevice('/devices/abc', 'abc')).toBe(false);
  });

  it('leaves when opening another page', () => {
    expect(hrefLeavesLiveDevice('/portal/chat', 'abc')).toBe(true);
    expect(hrefLeavesLiveDevice('/portal/alerts', 'abc')).toBe(true);
    expect(hrefLeavesLiveDevice('/portal/devices/other', 'abc')).toBe(true);
  });
});

describe('browser capture for picture-in-picture', () => {
  it('does not capture when this environment has no microphone API', async () => {
    expect(isBrowserCaptureActive()).toBe(false);
    expect(await startBrowserCaptureForPip()).toBe(false);
  });
});

describe('shouldCuePictureInPicture', () => {
  it('keeps a desktop live view eligible for the browser to open the floating window', () => {
    expect(shouldCuePictureInPicture(true)).toBe(true);
    expect(shouldCuePictureInPicture(false)).toBe(false);
  });
});

describe('shouldReleaseMeetPip', () => {
  it('keeps the desktop window when the main browser is covered', () => {
    expect(shouldReleaseMeetPip(false)).toBe(false);
  });

  it('releases the window after the user closes it', () => {
    expect(shouldReleaseMeetPip(true)).toBe(true);
  });
});

describe('pipLockedText', () => {
  const plain =
    'Sorry abse nhi bolugi ek line bhi dhyan rakhugi kya bol rhi hu aur samne wala kya samajhega bas haa haa Krugi jo bologe wo';

  it('scrambles a readable sentence while Encrypt is on', () => {
    expect(isPaddedChatBody(plain)).toBe(false);
    const locked = pipLockedText(plain, 'msg-golden');
    expect(locked).not.toBe(plain);
    expect(locked.split(/\s+/).every(word => word.length >= 7)).toBe(true);
    expect(pipLockedText(plain, 'msg-golden')).toBe(locked);
  });

  it('leaves an already padded body as the locked soup', () => {
    const padded = 'abcByexyz';
    expect(isPaddedChatBody(padded)).toBe(true);
    expect(pipLockedText(padded, 'msg-1')).toBe(padded);
  });
});
