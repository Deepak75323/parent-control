import {create} from 'zustand';
import type {ChatCallItem, ChatMemberItem} from '../types/api';

export type ChatCallSessionState = {
  /** True while this browser is joined to a call (media may be active). */
  joined: boolean;
  call: ChatCallItem | null;
  roomId: string | null;
  roomName: string | null;
  channelName: string | null;
  myUserId: string | null;
  members: ChatMemberItem[];
  audioOnly: boolean;
  minimized: boolean;
  canEnd: boolean;
  /** Discord-style self controls (shared with sidebar + call UI). */
  micOn: boolean;
  deafened: boolean;
  camOn: boolean;
  /** Bumped to ask ChatCallOverlay to leave the call. */
  leaveNonce: number;
  join: (input: {
    call: ChatCallItem;
    roomId: string;
    roomName: string;
    channelName?: string | null;
    myUserId: string;
    members?: ChatMemberItem[];
    audioOnly?: boolean;
    canEnd?: boolean;
  }) => void;
  patchCall: (call: ChatCallItem) => void;
  setMembers: (members: ChatMemberItem[]) => void;
  setChannelName: (channelName: string | null) => void;
  setMinimized: (minimized: boolean) => void;
  setCanEnd: (canEnd: boolean) => void;
  setMicOn: (micOn: boolean) => void;
  setDeafened: (deafened: boolean) => void;
  setCamOn: (camOn: boolean) => void;
  toggleMic: () => void;
  toggleDeafen: () => void;
  toggleCam: () => void;
  requestLeave: () => void;
  leaveLocal: () => void;
  clear: () => void;
};

const empty = {
  joined: false,
  call: null as ChatCallItem | null,
  roomId: null as string | null,
  roomName: null as string | null,
  channelName: null as string | null,
  myUserId: null as string | null,
  members: [] as ChatMemberItem[],
  audioOnly: false,
  minimized: false,
  canEnd: false,
  micOn: true,
  deafened: false,
  camOn: true,
  leaveNonce: 0,
};

export const useChatCallSessionStore = create<ChatCallSessionState>((set, get) => ({
  ...empty,
  join: input =>
    set({
      joined: true,
      call: input.call,
      roomId: input.roomId,
      roomName: input.roomName,
      channelName: input.channelName ?? null,
      myUserId: input.myUserId,
      members: input.members ?? [],
      audioOnly: input.audioOnly ?? false,
      minimized: false,
      canEnd: input.canEnd ?? false,
      micOn: true,
      deafened: false,
      camOn: !(input.audioOnly ?? false),
      leaveNonce: 0,
    }),
  patchCall: call => set({call}),
  setMembers: members => set({members}),
  setChannelName: channelName => set({channelName}),
  setMinimized: minimized => set({minimized}),
  setCanEnd: canEnd => set({canEnd}),
  setMicOn: micOn => set({micOn}),
  setDeafened: deafened => set({deafened}),
  setCamOn: camOn => set({camOn}),
  toggleMic: () => {
    const {deafened, micOn} = get();
    if (deafened) {
      // Undeafen first when user wants to speak (Discord-like).
      set({deafened: false, micOn: true});
      return;
    }
    set({micOn: !micOn});
  },
  toggleDeafen: () => {
    const {deafened} = get();
    if (!deafened) {
      set({deafened: true, micOn: false});
      return;
    }
    // Undeafen keeps mic muted — user unmutes explicitly (Discord).
    set({deafened: false});
  },
  toggleCam: () => set(s => ({camOn: !s.camOn})),
  requestLeave: () => set(s => ({leaveNonce: s.leaveNonce + 1})),
  leaveLocal: () => set({...empty}),
  clear: () => set({...empty}),
}));
