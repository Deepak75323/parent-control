import {useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore, type FormEvent, type PointerEvent as ReactPointerEvent} from 'react';
import {useAuthStore} from '../auth/authStore';
import {chatApi} from '../api/services';
import {decodeIncomingChatBody, encodeOutgoingChatBody, openChatDecryptSession} from '../lib/chatDecryptSession';
import {resolveChatDisplayNickname} from '../lib/chatDisplayNickname';
import {canClearHistoryForEveryone, type ChatMemberRole} from '../lib/chatMemberPermissions';
import {withMonotonicReceiptStatuses} from '../lib/chatReceiptMonotonic';
import {receiptStatusForViewer} from '../lib/chatReceiptPrivacy';
import {decodeChatMessagePlaintext} from '../lib/chatMessageObfuscation';
import {isEncryptedChatBody} from '../lib/chatMessageCrypto';
import {cameraPaneHeight, chatRoomForPip, defaultCameraPane, isPaddedChatBody, isServerChatRoom, MEET_PIP_OPEN_SIZE, meetPipWindowSize, mergeChatMessages, pickLatestChatRoom, pipChatChoices, pipLockedText, pipMessageActionIds, type PipMessageActionId} from './browserPip';
import {CHAT_QUICK_REACTION_EMOJIS} from '../lib/chatReactionEmojis';
import {
  CHAT_EMOJI_CATEGORIES,
  defaultEmojiCategoryId,
  loadRecentEmojis,
  rememberEmoji,
  type EmojiCategory,
} from '../lib/chatEmojiCatalog';
import {formatChatTypingLine} from '../lib/chatTypingLabel';
import {insertTextAtSelection} from '../lib/insertTextAtSelection';
import {
  countMergedEmojiLines,
  normalizeMergedEmojiArt,
} from '../lib/chatMergedEmojiFormat';
import {CHAT_MESSAGE_BODY_MAX, CHAT_MESSAGE_PLAINTEXT_MAX} from '../lib/chatMessageLimits';
import {deletedNoticeTombstone, deletedNoticeVisibleUntil, withDeletedNoticeRead} from '../lib/deletedNotice';
import {
  chatMessageTooLongAfterSealingError,
  trimPlaintextToSealedLimit,
} from '../lib/chatMessageSealedLength';
import {
  addCustomMergedEmoji,
  allMergedEmojiCollections,
  filterMergedEmojiItems,
  loadCustomMergedEmojis,
  previewMergedEmojiLines,
} from '../lib/chatMergedEmojiCatalog';
import {chatPipRoomId, subscribeChatPipRoom} from './chatPipTarget';
import {CameraFrameViewer} from '../components/CameraFrameViewer';
import {nextViewerRotateDeg, prevViewerRotateDeg, viewerVideoRotateStyle} from '../lib/viewerVideoRotate';
import {isChatViewUnlocked, setChatViewUnlocked, subscribeChatViewUnlocked} from '../lib/chatViewUnlock';
import {
  isShowDeletedMessagesForOwner,
  messageForOwnerDeletedView,
  setShowDeletedMessagesForOwner,
  subscribeShowDeletedMessagesForOwner,
} from '../lib/ownerDeletedMessages';
import {
  buildMentionCandidates,
  collectMentionTokens,
  filterMentionCandidates,
  findActiveMentionQuery,
  insertMention,
  splitMessageText,
} from '../lib/chatMentions';
import type {ChatMemberItem, ChatMessageItem, ChatMessageReplyPreview, ChatRoomSummary} from '../types/api';
import {useLiveStreamStore} from './liveStreamStore';
import {pipMediaView} from './pipMediaView';
import {useRealtimeStore} from '../realtime/realtimeStore';

type Props = {
  onClose: () => void;
};

type PendingAction = {
  message: string;
  run: () => Promise<void>;
};

function meetEmojiCategories(recent: string[]): EmojiCategory[] {
  if (recent.length === 0) {
    return CHAT_EMOJI_CATEGORIES;
  }
  return [{id: 'recent', label: 'Recent', icon: '🕘', emojis: recent}, ...CHAT_EMOJI_CATEGORIES];
}

/** PiP nodes belong to the floating window, not the opener page's Node. */
function pointerInside(menu: HTMLElement | null, target: EventTarget | null): boolean {
  if (!menu || target == null) {
    return false;
  }
  const ViewNode = menu.ownerDocument.defaultView?.Node;
  if (!ViewNode || !(target instanceof ViewNode)) {
    return false;
  }
  return menu.contains(target);
}

function focusComposer(input: HTMLInputElement | HTMLTextAreaElement | null) {
  if (!input) {
    return;
  }
  const view = input.ownerDocument.defaultView ?? window;
  view.requestAnimationFrame(() => input.focus());
}

function pipMessageActionLabel(action: PipMessageActionId): string {
  switch (action) {
    case 'react':
      return 'React';
    case 'reply':
      return 'Reply';
    case 'mark-unread':
      return 'Mark as unread';
    case 'edit':
      return 'Edit';
    case 'delete-me':
      return 'Delete for me';
    case 'delete-everyone':
      return 'Delete for everyone';
    case 'copy':
      return 'Copy';
  }
}

function replyQuoteText(preview: ChatMessageReplyPreview, unlocked: boolean): string {
  if (preview.deleted) {
    return 'This note is deleted';
  }
  const raw = preview.bodyPreview?.trim() ?? '';
  if (!raw) {
    return preview.messageType === 'IMAGE' ? 'Photo' : preview.messageType === 'VIDEO' ? 'Video' : 'Message';
  }
  if (!unlocked) {
    return pipLockedText(raw, preview.messageId);
  }
  if (isEncryptedChatBody(raw) || isPaddedChatBody(raw)) {
    return isPaddedChatBody(raw) ? decodeChatMessagePlaintext(raw) : raw;
  }
  return raw;
}

async function visibleChatBody(body: string, unlocked: boolean, id: string): Promise<string> {
  if (!body) {
    return '';
  }
  if (!unlocked) {
    return pipLockedText(body, id);
  }
  if (isEncryptedChatBody(body) || isPaddedChatBody(body)) {
    return decodeIncomingChatBody(body, true);
  }
  return body;
}

export function MeetStylePip({onClose}: Props) {
  const deviceName = useLiveStreamStore(s => s.deviceName);
  const sessionId = useLiveStreamStore(s => s.sessionId);
  const remoteStream = useLiveStreamStore(s => s.remoteStream);
  const streamType = useLiveStreamStore(s => s.streamType);
  const muted = useLiveStreamStore(s => s.muted);
  const toggleMute = useLiveStreamStore(s => s.toggleMute);
  const stop = useLiveStreamStore(s => s.stop);
  const viewRotate = useLiveStreamStore(s => s.viewRotate);
  const setViewRotate = useLiveStreamStore(s => s.setViewRotate);
  const cameraFacing = useLiveStreamStore(s => s.cameraFacing);
  const liveMessages = useRealtimeStore(s => s.liveChatMessages);
  const reactionPatches = useRealtimeStore(s => s.reactionPatches);
  const deletedChatMessageIds = useRealtimeStore(s => s.deletedChatMessageIds);
  const receiptPatches = useRealtimeStore(s => s.receiptPatches);
  const readReceiptsEnabled = useAuthStore(s => s.profile?.readReceiptsEnabled);
  const videoRef = useRef<HTMLVideoElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const [rooms, setRooms] = useState<ChatRoomSummary[]>([]);
  const [room, setRoom] = useState<ChatRoomSummary | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [shown, setShown] = useState<Record<string, string>>({});
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [pinOpen, setPinOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [pinMode, setPinMode] = useState<'unlock' | 'create'>('unlock');
  const [pinBusy, setPinBusy] = useState(false);
  const [cameraHidden, setCameraHidden] = useState(false);
  const [members, setMembers] = useState<ChatMemberItem[]>([]);
  const [cursor, setCursor] = useState(0);
  const [mentionIndex, setMentionIndex] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [replyingTo, setReplyingTo] = useState<ChatMessageItem | null>(null);
  const [editingMessage, setEditingMessage] = useState<ChatMessageItem | null>(null);
  const [messageMenu, setMessageMenu] = useState<{id: string; top: number; left: number; up: boolean} | null>(null);
  const [reactPickerFor, setReactPickerFor] = useState<string | null>(null);
  const messageMenuRef = useRef<HTMLDivElement>(null);
  const [minimized, setMinimized] = useState(false);
  const [cameraBox, setCameraBox] = useState<{w: number; h: number} | null>(null);
  const cameraDrag = useRef(false);
  const cameraChosen = useRef(false);
  const shellRef = useRef<HTMLDivElement>(null);
  const composeRef = useRef<HTMLTextAreaElement>(null);
  const emojiRootRef = useRef<HTMLDivElement>(null);
  const emojiButtonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const typingIdleTimerRef = useRef<number | null>(null);
  const typingActiveRef = useRef(false);
  const lastTypingPulseAtRef = useRef(0);
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [emojiMode, setEmojiMode] = useState<'emoji' | 'merged'>('emoji');
  const [emojiCategoryId, setEmojiCategoryId] = useState('smileys');
  const [emojiCategories, setEmojiCategories] = useState<EmojiCategory[]>(CHAT_EMOJI_CATEGORIES);
  const [mergedPaste, setMergedPaste] = useState('');
  const [mergedPasteName, setMergedPasteName] = useState('');
  const [mergedPasteError, setMergedPasteError] = useState<string | null>(null);
  const [showMergedPaste, setShowMergedPaste] = useState(false);
  const chatTypingByRoom = useRealtimeStore(s => s.chatTypingByRoom);
  const pruneExpiredChatTyping = useRealtimeStore(s => s.pruneExpiredChatTyping);
  const sentPlain = useRef<Record<string, string>>({});
  const unlocked = useSyncExternalStore(subscribeChatViewUnlocked, isChatViewUnlocked, () => false);
  const preferredRoomId = useSyncExternalStore(subscribeChatPipRoom, chatPipRoomId, () => null);
  const showDeletedMessages = useSyncExternalStore(
    subscribeShowDeletedMessagesForOwner,
    isShowDeletedMessagesForOwner,
    () => false,
  );

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !remoteStream) {
      return;
    }
    el.srcObject = remoteStream;
    el.muted = streamType === 'SCREEN' ? muted : true;
    void el.play().catch(() => undefined);
  }, [remoteStream, streamType, muted, cameraHidden, sessionId, minimized]);

  useLayoutEffect(() => {
    const live = Boolean(sessionId) && streamType !== 'AUDIO';
    if (minimized || !live || cameraHidden || cameraChosen.current) {
      return;
    }
    const shell = shellRef.current;
    if (!shell || shell.clientHeight < 200 || shell.clientWidth < 160) {
      return;
    }
    cameraChosen.current = true;
    setCameraBox(defaultCameraPane(shell.clientWidth, shell.clientHeight));
  }, [sessionId, streamType, cameraHidden, minimized]);

  useEffect(() => {
    let cancelled = false;
    void chatApi
      .listRooms()
      .then(({data}) => {
        if (!cancelled) {
          const list = data.data ?? [];
          setRooms(list);
          setRoom(chatRoomForPip(list, chatPipRoomId(), pickLatestChatRoom(list)));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setNotice('Could not open Notes');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!preferredRoomId || rooms.length === 0) {
      return;
    }
    const next = rooms.find(item => item.id === preferredRoomId);
    if (!next || next.id === room?.id) {
      return;
    }
    setReplyingTo(null);
    setMessages([]);
    setRoom(next);
  }, [preferredRoomId, rooms, room?.id]);

  useEffect(() => {
    if (!room) {
      return;
    }
    let cancelled = false;
    void chatApi.listMessages(room.id, 0, 40, undefined, showDeletedMessages && Boolean(room.owner)).then(({data}) => {
      if (!cancelled) {
        setMessages(data.data ?? []);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [room, showDeletedMessages]);

  useEffect(() => {
    if (!room) {
      return;
    }
    let cancelled = false;
    void chatApi.listMembers(room.id).then(({data}) => {
      if (!cancelled) {
        setMembers(data.data ?? []);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [room]);

  useEffect(() => {
    if (!messageMenu) {
      return;
    }
    const doc = messageMenuRef.current?.ownerDocument ?? document;
    const onPointer = (event: Event) => {
      if (pointerInside(messageMenuRef.current, event.target)) {
        return;
      }
      setMessageMenu(null);
      setReactPickerFor(null);
    };
    doc.addEventListener('mousedown', onPointer);
    return () => doc.removeEventListener('mousedown', onPointer);
  }, [messageMenu]);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    const doc = menuRef.current?.ownerDocument ?? document;
    const onPointer = (event: Event) => {
      if (pointerInside(menuRef.current, event.target)) {
        return;
      }
      setMenuOpen(false);
    };
    doc.addEventListener('mousedown', onPointer);
    return () => doc.removeEventListener('mousedown', onPointer);
  }, [menuOpen]);

  useEffect(() => {
    if (!room) {
      return;
    }
    const incoming = liveMessages.filter(message => message.roomId === room.id);
    if (incoming.length === 0) {
      return;
    }
    setMessages(current => mergeChatMessages(current, incoming));
  }, [liveMessages, room]);

  useEffect(() => {
    let cancelled = false;
    void Promise.all(
      messages.map(async message => {
        const local = unlocked ? sentPlain.current[message.id] : undefined;
        if (local) {
          return [message.id, local] as const;
        }
        const text = await visibleChatBody(message.body, unlocked, message.id);
        return [message.id, text] as const;
      }),
    ).then(pairs => {
      if (!cancelled) {
        setShown(Object.fromEntries(pairs));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [messages, unlocked]);

  useEffect(() => {
    const log = logRef.current;
    if (!log) {
      return;
    }
    log.scrollTop = log.scrollHeight;
  }, [messages, shown]);

  const roomId = room?.id ?? null;

  useEffect(() => {
    const timer = window.setInterval(() => pruneExpiredChatTyping(), 1000);
    return () => window.clearInterval(timer);
  }, [pruneExpiredChatTyping]);

  useEffect(() => {
    return () => {
      if (typingIdleTimerRef.current != null) {
        window.clearTimeout(typingIdleTimerRef.current);
        typingIdleTimerRef.current = null;
      }
      if (typingActiveRef.current && roomId) {
        typingActiveRef.current = false;
        void chatApi.notifyTyping(roomId, false).catch(() => undefined);
      }
    };
  }, [roomId]);

  const typingLine = useMemo(() => {
    if (!roomId) {
      return '';
    }
    const now = Date.now();
    const peers = (chatTypingByRoom[roomId] ?? []).filter(peer => peer.expiresAt > now);
    return formatChatTypingLine(peers);
  }, [chatTypingByRoom, roomId]);

  function clearTypingIdleTimer() {
    if (typingIdleTimerRef.current != null) {
      window.clearTimeout(typingIdleTimerRef.current);
      typingIdleTimerRef.current = null;
    }
  }

  function publishTyping(typing: boolean) {
    if (!roomId) {
      return;
    }
    if (typingActiveRef.current === typing) {
      return;
    }
    typingActiveRef.current = typing;
    void chatApi.notifyTyping(roomId, typing).catch(() => undefined);
  }

  function stopTypingSignal() {
    clearTypingIdleTimer();
    lastTypingPulseAtRef.current = 0;
    publishTyping(false);
  }

  function pulseTypingFromDraft(nextDraft: string) {
    if (!roomId || editingMessage) {
      return;
    }
    if (!nextDraft.trim()) {
      stopTypingSignal();
      return;
    }
    const now = Date.now();
    if (!typingActiveRef.current || now - lastTypingPulseAtRef.current > 2000) {
      typingActiveRef.current = false;
      lastTypingPulseAtRef.current = now;
      publishTyping(true);
    }
    clearTypingIdleTimer();
    typingIdleTimerRef.current = window.setTimeout(() => {
      publishTyping(false);
    }, 2500);
  }

  useEffect(() => {
    if (!emojiOpen) {
      return;
    }
    const doc = composeRef.current?.ownerDocument ?? document;
    function handlePointer(event: Event) {
      const target = event.target;
      if (pointerInside(emojiRootRef.current, target) || pointerInside(emojiButtonRef.current, target)) {
        return;
      }
      setEmojiOpen(false);
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setEmojiOpen(false);
      }
    }
    doc.addEventListener('mousedown', handlePointer);
    doc.addEventListener('keydown', handleKey);
    return () => {
      doc.removeEventListener('mousedown', handlePointer);
      doc.removeEventListener('keydown', handleKey);
    };
  }, [emojiOpen]);

  function artForSend(raw: string): string {
    const normalized = normalizeMergedEmojiArt(raw).slice(0, CHAT_MESSAGE_PLAINTEXT_MAX);
    return trimPlaintextToSealedLimit(normalized);
  }

  function insertEmoji(emoji: string) {
    const input = composeRef.current;
    const start = input?.selectionStart ?? cursor;
    const end = input?.selectionEnd ?? cursor;
    const {nextText, cursor: nextCursor} = insertTextAtSelection(draft, emoji, start, end);
    setDraft(nextText);
    setCursor(nextCursor);
    setEmojiCategories(meetEmojiCategories(rememberEmoji(emoji)));
    pulseTypingFromDraft(nextText);
    const view = input?.ownerDocument.defaultView ?? window;
    view.requestAnimationFrame(() => {
      if (!input) {
        return;
      }
      input.focus();
      input.setSelectionRange(nextCursor, nextCursor);
    });
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    const text = draft.trim();
    if (!text || !room || sending) {
      return;
    }
    if (editingMessage) {
      setSending(true);
      setNotice(null);
      try {
        const body = await encodeOutgoingChatBody(text);
        if (body.length > CHAT_MESSAGE_BODY_MAX) {
          setNotice(chatMessageTooLongAfterSealingError(text));
          return;
        }
        const {data} = await chatApi.editMessage(room.id, editingMessage.id, body);
        const saved = data.data;
        if (saved) {
          sentPlain.current[saved.id] = text;
          setMessages(current => mergeChatMessages(current, [saved]));
        }
        setDraft('');
        setEmojiOpen(false);
        setEditingMessage(null);
      } catch (err) {
        setNotice(err instanceof Error ? err.message : 'Could not edit');
      } finally {
        setSending(false);
        focusComposer(composeRef.current);
      }
      return;
    }
    setSending(true);
    setNotice(null);
    const replyId = replyingTo?.id;
    try {
      const body = await encodeOutgoingChatBody(text);
      if (body.length > CHAT_MESSAGE_BODY_MAX) {
        setNotice(chatMessageTooLongAfterSealingError(text));
        return;
      }
      const {data} = await chatApi.sendMessage(room.id, body, replyId);
      const saved = data.data;
      if (saved) {
        sentPlain.current[saved.id] = text;
        setMessages(current => mergeChatMessages(current, [saved]));
      }
      setDraft('');
      setEmojiOpen(false);
      stopTypingSignal();
      setReplyingTo(null);
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'Could not send');
    } finally {
      setSending(false);
      focusComposer(composeRef.current);
    }
  }

  function beginReply(message: ChatMessageItem) {
    if (message.deleted) {
      return;
    }
    setEditingMessage(null);
    setMessageMenu(null);
    setReactPickerFor(null);
    setReplyingTo(message);
    focusComposer(composeRef.current);
  }

  function beginEdit(message: ChatMessageItem) {
    setReplyingTo(null);
    setMessageMenu(null);
    setReactPickerFor(null);
    setEditingMessage(message);
    setDraft(shown[message.id] ?? '');
    focusComposer(composeRef.current);
  }

  function openMessageMenu(messageId: string, anchor: HTMLElement) {
    const view = anchor.ownerDocument.defaultView;
    const rect = anchor.getBoundingClientRect();
    const height = view?.innerHeight ?? 640;
    const up = rect.bottom + 220 > height && rect.top > 180;
    setReactPickerFor(null);
    setMessageMenu({
      id: messageId,
      top: up ? rect.top - 4 : rect.bottom + 4,
      left: Math.max(8, Math.min(rect.left, (view?.innerWidth ?? 380) - 196)),
      up,
    });
  }

  async function reactTo(message: ChatMessageItem, emoji: string) {
    if (!room) {
      return;
    }
    setMessageMenu(null);
    setReactPickerFor(null);
    try {
      const {data} = await chatApi.setMessageReaction(room.id, message.id, emoji);
      const reactions = data.data ?? [];
      setMessages(current => current.map(item => (item.id === message.id ? {...item, reactions} : item)));
      useRealtimeStore.getState().updateLiveChatMessageReactions(message.id, reactions);
    } catch {
      setNotice('Could not update reaction');
    }
  }

  function runMessageAction(message: ChatMessageItem, action: PipMessageActionId) {
    if (action === 'react') {
      setReactPickerFor(message.id);
      return;
    }
    setMessageMenu(null);
    setReactPickerFor(null);
    if (action === 'reply') {
      beginReply(message);
      return;
    }
    if (action === 'edit') {
      beginEdit(message);
      return;
    }
    if (action === 'copy') {
      const text = shown[message.id];
      if (!text) {
        return;
      }
      const clipboard = messageMenuRef.current?.ownerDocument.defaultView?.navigator.clipboard;
      void (clipboard ?? navigator.clipboard).writeText(text).catch(() => setNotice('Could not copy message'));
      return;
    }
    if (action === 'mark-unread') {
      if (!room) {
        return;
      }
      void chatApi.markUnread(room.id, message.id).catch(() => setNotice('Could not mark as unread'));
      return;
    }
    if (!room) {
      return;
    }
    const everyone = action === 'delete-everyone';
    askPip(everyone ? 'Delete this note for everyone?' : 'Delete this note for you?', async () => {
      await chatApi.deleteMessage(room.id, message.id, everyone ? 'FOR_EVERYONE' : 'FOR_ME');
      setMessages(current =>
        everyone
          ? current.map(item =>
              item.id === message.id
                ? showDeletedMessages
                  ? {...item, deleted: true}
                  : deletedNoticeTombstone(item, deletedNoticeVisibleUntil())
                : item,
            )
          : current.filter(item => item.id !== message.id),
      );
      if (!everyone) {
        useRealtimeStore.getState().removeLiveChatMessage(message.id);
      }
    });
  }

  async function startDecrypt() {
    if (!room || !(room.owner || room.canDecrypt)) {
      return;
    }
    setNotice(null);
    try {
      const {data} = await chatApi.decryptStatus();
      setPinMode(data.data?.configured ? 'unlock' : 'create');
      setPinOpen(true);
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'Could not check decrypt status');
    }
  }

  async function submitPin(event: FormEvent) {
    event.preventDefault();
    if (!room || pinBusy || pin.trim().length < 4) {
      return;
    }
    setPinBusy(true);
    setNotice(null);
    try {
      let salt: string | undefined;
      if (pinMode === 'create') {
        const created = await chatApi.createDecryptPin(pin.trim());
        salt = created.data.data?.cryptoSalt;
      }
      const unlockedSession = await chatApi.unlockDecrypt(room.id, pin.trim());
      salt = salt ?? unlockedSession.data.data?.cryptoSalt;
      if (!salt) {
        throw new Error('Decrypt salt missing from server');
      }
      await openChatDecryptSession(pin.trim(), salt);
      setChatViewUnlocked(true);
      setPin('');
      setPinOpen(false);
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'Could not decrypt');
    } finally {
      setPinBusy(false);
    }
  }

  const canLock = Boolean(room && (room.owner || room.canDecrypt));
  const turned = viewRotate === 90 || viewRotate === 270;
  const openPane = defaultCameraPane(MEET_PIP_OPEN_SIZE.width, MEET_PIP_OPEN_SIZE.height);
  const videoStyle = viewerVideoRotateStyle(
    viewRotate,
    cameraBox?.w ?? openPane.w,
    cameraBox ? Math.max(1, cameraBox.h - 8) : openPane.h,
    'contain',
  );
  const mentionCandidates = buildMentionCandidates(members);
  const activeMention = findActiveMentionQuery(draft, cursor);
  const mentionChoices = activeMention ? filterMentionCandidates(mentionCandidates, activeMention.query) : [];
  const safeMentionIndex = mentionChoices.length === 0 ? 0 : mentionIndex % mentionChoices.length;
  const threaded = withMonotonicReceiptStatuses(
    messages.flatMap(message => {
      const visible = messageForOwnerDeletedView(message, {
        removedForEveryone: Boolean(deletedChatMessageIds[message.id]),
        showDeleted: showDeletedMessages,
        viewerIsRoomOwner: Boolean(room?.owner),
        nowMs: Date.now(),
      });
      if (!visible) {
        return [];
      }
      const raw = receiptPatches[visible.id] ?? visible.receiptStatus;
      return [
        {
          ...visible,
          receiptStatus: receiptStatusForViewer(raw, readReceiptsEnabled),
        },
      ];
    }),
  );

  function chooseMention(index: number) {
    const candidate = mentionChoices[index];
    if (!candidate || !activeMention) {
      return;
    }
    const next = insertMention(draft, activeMention.start, activeMention.end, candidate.token);
    setDraft(next.nextText);
    setCursor(next.cursor);
    setMentionIndex(0);
    window.requestAnimationFrame(() => {
      composeRef.current?.focus();
      composeRef.current?.setSelectionRange(next.cursor, next.cursor);
    });
  }

  function insertAtSign() {
    const input = composeRef.current;
    const start = input?.selectionStart ?? cursor;
    const end = input?.selectionEnd ?? cursor;
    const next = `${draft.slice(0, start)}@${draft.slice(end)}`;
    const nextCursor = start + 1;
    setDraft(next);
    setCursor(nextCursor);
    setMentionIndex(0);
    window.requestAnimationFrame(() => {
      composeRef.current?.focus();
      composeRef.current?.setSelectionRange(nextCursor, nextCursor);
    });
  }

  function pressMenu(run: () => void) {
    return (event: {preventDefault: () => void; stopPropagation: () => void}) => {
      event.preventDefault();
      event.stopPropagation();
      setMenuOpen(false);
      run();
    };
  }

  function askPip(message: string, run: () => Promise<void>) {
    setMenuOpen(false);
    setPending({message, run});
  }

  async function markNotebookRead() {
    if (!room) {
      return;
    }
    const lastIncoming = [...messages].reverse().find(message => !message.ownMessage);
    if (!lastIncoming) {
      return;
    }
    await chatApi.markRead(room.id, lastIncoming.id);
    setMessages(current =>
      current.map(message => (message.ownMessage ? message : withDeletedNoticeRead(message))),
    );
    setRoom(current => (current ? {...current, unreadCount: 0} : current));
    setNotice(null);
  }

  async function clearNotebook() {
    if (!room) {
      return;
    }
    const roomId = room.id;
    const roomName = room.name;
    askPip(`Clear messages in "${roomName}" on this device only? Other members will still see the chat.`, async () => {
      await chatApi.clearChat(roomId);
      setMessages([]);
      setNotice('Chat cleared on this device.');
    });
  }

  async function clearNotebookHistory() {
    if (!room) {
      return;
    }
    const roomId = room.id;
    const roomName = room.name;
    askPip(`Remove all messages in "${roomName}" for every member?`, async () => {
      await chatApi.clearHistory(roomId);
      setMessages([]);
      setNotice('History cleared for everyone.');
    });
  }

  async function deleteOrLeaveNotebook() {
    if (!room) {
      return;
    }
    const roomId = room.id;
    const owned = room.owner;
    const roomName = room.name;
    askPip(
      owned
        ? `Delete "${roomName}"? This removes the notebook for all members.`
        : `Leave "${roomName}"? You can be invited again later.`,
      async () => {
        if (owned) {
          await chatApi.deleteRoom(roomId);
        } else {
          await chatApi.leaveRoom(roomId);
        }
        setRooms(current => current.filter(item => item.id !== roomId));
        setRoom(null);
        setMessages([]);
      },
    );
  }

  function openNotebook(next: ChatRoomSummary) {
    if (next.id === room?.id) {
      return;
    }
    setReplyingTo(null);
    setMessages([]);
    setRoom(next);
  }

  const liveCamera = Boolean(sessionId) && streamType !== 'AUDIO';

  function onBannerPointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    if (!liveCamera || cameraHidden || event.button !== 0) {
      return;
    }
    const target = event.target as {closest?: (selector: string) => unknown} | null;
    if (target?.closest?.('button, .meet-menu')) {
      return;
    }
    cameraDrag.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    moveCameraBanner(event.clientY);
  }

  function onBannerPointerMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (!cameraDrag.current) {
      return;
    }
    moveCameraBanner(event.clientY);
  }

  function onBannerPointerUp() {
    cameraDrag.current = false;
  }

  function moveCameraBanner(pointerY: number) {
    const shell = shellRef.current;
    if (!shell) {
      return;
    }
    const rect = shell.getBoundingClientRect();
    const height = cameraPaneHeight(pointerY, rect.top, rect.height);
    cameraChosen.current = true;
    setCameraBox({w: Math.max(1, shell.clientWidth - 24), h: height});
  }

  function toggleMinimized() {
    const next = !minimized;
    const size = meetPipWindowSize(next, Boolean(sessionId) && streamType !== 'AUDIO');
    const view = shellRef.current?.ownerDocument.defaultView;
    try {
      view?.resizeTo(size.width, size.height);
    } catch {
      // The chat still collapses if the window refuses the resize.
    }
    setMinimized(next);
  }

  const notebookRole = (room?.myRole ?? (room?.owner ? 'OWNER' : 'MEMBER')) as ChatMemberRole;
  const chatChoices = pipChatChoices(rooms);
  const dockTitle = liveCamera ? (deviceName ?? 'Live stream') : (room?.name ?? 'Notes');

  if (minimized) {
    return (
      <div className="meet-pip meet-pip--dock" ref={shellRef}>
        <style>{MEET_PIP_CSS}</style>
        {liveCamera ? (
          <video ref={videoRef} autoPlay playsInline muted className="meet-dock-hold" />
        ) : null}
        <button
          type="button"
          className="meet-dock-main"
          aria-label={liveCamera ? `Restore live stream for ${deviceName ?? 'device'}` : 'Restore chat'}
          onClick={toggleMinimized}>
          <span className="meet-dock-dot" />
          <span className="meet-dock-title">{dockTitle}</span>
          {liveCamera ? <span className="meet-dock-live">LIVE</span> : null}
        </button>
        <button type="button" className="meet-dock-close" aria-label="Close picture in picture" onClick={onClose}>
          ×
        </button>
      </div>
    );
  }

  return (
    <div className="meet-pip" ref={shellRef}>
      <style>{MEET_PIP_CSS}</style>
      {liveCamera ? (
      <div
        className={`meet-stage sized${cameraHidden ? ' hidden' : ''}`}
        style={{height: (cameraBox ?? openPane).h}}>
        <div className={`meet-video-card${turned ? ' turned' : ''}`}>
          {streamType === 'CAMERA' ? (
            <CameraFrameViewer
              stream={remoteStream}
              objectFit="contain"
              viewerRotate={viewRotate}
              facingHint={cameraFacing}
            />
          ) : (
            <video ref={videoRef} autoPlay playsInline style={videoStyle} />
          )}
          <span className="meet-name">{deviceName ?? 'Child'}</span>
          <div className="meet-video-actions">
            <button
              type="button"
              className="meet-rot"
              aria-label="Minimize picture in picture"
              onClick={toggleMinimized}>
              −
            </button>
            <button
              type="button"
              className="meet-rot"
              aria-label="Rotate left"
              onClick={() => setViewRotate(deg => prevViewerRotateDeg(deg))}>
              ↺
            </button>
            <button
              type="button"
              className="meet-rot"
              aria-label="Rotate right"
              onClick={() => setViewRotate(deg => nextViewerRotateDeg(deg))}>
              ↻
            </button>
          </div>
        </div>
      </div>
      ) : null}
      <div
        className={`meet-titlebar${liveCamera && !cameraHidden ? ' draggable' : ''}`}
        title={liveCamera && !cameraHidden ? 'Drag to resize the camera and chat' : undefined}
        onPointerDown={onBannerPointerDown}
        onPointerMove={onBannerPointerMove}
        onPointerUp={onBannerPointerUp}>
        <div className="meet-title">{room?.name ?? 'Notes'}</div>
        {canLock ? (
          unlocked ? (
            <button type="button" className="meet-lock" onClick={() => setChatViewUnlocked(false)}>
              Encrypt
            </button>
          ) : (
            <button type="button" className="meet-lock" onClick={() => void startDecrypt()}>
              Decrypt
            </button>
          )
        ) : null}
        <div className="meet-menu" ref={menuRef}>
          <button
            type="button"
            className="meet-more"
            aria-label="More options"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            disabled={!room}
            onClick={() => setMenuOpen(open => !open)}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
              <circle cx="12" cy="5" r="2" />
              <circle cx="12" cy="12" r="2" />
              <circle cx="12" cy="19" r="2" />
            </svg>
          </button>
          {menuOpen && room ? (
            <div className="meet-menu-pop" role="menu">
              {chatChoices.servers.length > 0
                ? [...chatChoices.servers, ...chatChoices.directs].map(choice => (
                    <button
                      key={choice.id}
                      type="button"
                      role="menuitem"
                      className={choice.id === room.id ? 'current' : undefined}
                      onMouseDown={pressMenu(() => openNotebook(choice))}>
                      {isServerChatRoom(choice) ? 'Server' : 'Direct'} · {choice.name}
                    </button>
                  ))
                : null}
              {(room.unreadCount ?? 0) > 0 ? (
                <button type="button" role="menuitem" onMouseDown={pressMenu(() => void markNotebookRead().catch(() => setNotice('Could not mark as read')))}>
                  Mark as read
                </button>
              ) : null}
              {canLock ? (
                <button
                  type="button"
                  role="menuitem"
                  onMouseDown={pressMenu(() => {
                    if (unlocked) {
                      setChatViewUnlocked(false);
                    } else {
                      void startDecrypt();
                    }
                  })}>
                  {unlocked ? 'Lock chat' : 'Decrypt chat'}
                </button>
              ) : null}
              {room.owner ? (
                <button
                  type="button"
                  role="menuitem"
                  onMouseDown={pressMenu(() => setShowDeletedMessagesForOwner(!showDeletedMessages))}>
                  {showDeletedMessages ? 'Hide deleted messages' : 'Show deleted messages'}
                </button>
              ) : null}
              <button
                type="button"
                role="menuitem"
                onMouseDown={pressMenu(() => {
                  void clearNotebook().catch(() => setNotice('Could not clear chat'));
                })}>
                Clear chat
              </button>
              {canClearHistoryForEveryone(notebookRole) ? (
                <button
                  type="button"
                  role="menuitem"
                  className="danger"
                  onMouseDown={pressMenu(() => {
                    void clearNotebookHistory().catch(() => setNotice('Could not clear history'));
                  })}>
                  Clear history
                </button>
              ) : null}
              <button
                type="button"
                role="menuitem"
                className="danger"
                onMouseDown={pressMenu(() => {
                  void deleteOrLeaveNotebook().catch(() =>
                    setNotice(room.owner ? 'Could not delete room' : 'Could not leave room'),
                  );
                })}>
                {room.owner ? 'Delete room' : 'Leave room'}
              </button>
            </div>
          ) : null}
        </div>
        <button
          type="button"
          className="meet-min"
          aria-label={minimized ? 'Restore picture in picture' : 'Minimize picture in picture'}
          onClick={toggleMinimized}>
          {minimized ? '▢' : '−'}
        </button>
        <button type="button" className="meet-close" aria-label="Close picture in picture" onClick={onClose}>
          ×
        </button>
      </div>
      {pending ? (
        <div className="meet-confirm">
          <p>{pending.message}</p>
          <div className="meet-confirm-actions">
            <button type="button" onMouseDown={event => event.preventDefault()} onClick={() => setPending(null)}>
              Cancel
            </button>
            <button
              type="button"
              className="danger"
              onMouseDown={event => event.preventDefault()}
              onClick={() => {
                const run = pending.run;
                setPending(null);
                void run().catch(() => setNotice('Could not finish that'));
              }}>
              Confirm
            </button>
          </div>
        </div>
      ) : null}
      {pinOpen ? (
        <form className="meet-pin" onSubmit={event => void submitPin(event)}>
          <input
            type="password"
            inputMode="numeric"
            autoComplete="off"
            value={pin}
            maxLength={8}
            placeholder={pinMode === 'create' ? 'Create a 4-digit code' : 'Decrypt code'}
            aria-label="Decrypt code"
            onChange={event => setPin(event.target.value)}
          />
          <button type="submit" disabled={pinBusy || pin.trim().length < 4}>
            {pinMode === 'create' ? 'Create' : 'Unlock'}
          </button>
        </form>
      ) : null}
      <div className="meet-log" ref={logRef}>
        {!room ? <p className="meet-empty">No notebook yet</p> : null}
        {room && messages.length === 0 ? <p className="meet-empty">No messages yet</p> : null}
        {threaded.map(message => (
          <div key={message.id} className={`meet-bubble${message.ownMessage ? ' own' : ''}`}>
            {message.ownMessage ? null : (
              <span className="meet-bubble-label">
                {resolveChatDisplayNickname(message.senderNickname, message.senderLabel)}
              </span>
            )}
            <div className="meet-bubble-text">
              {message.replyTo ? (
                <span className="meet-reply">
                  {replyQuoteText(message.replyTo, unlocked)}
                </span>
              ) : null}
              {message.deleted ? (
                <span className="meet-deleted">This note is deleted</span>
              ) : null}
              {message.messageType === 'IMAGE' || message.messageType === 'VIDEO' ? (
                message.deleted ? null : (
                  <MeetPipMedia
                    message={message}
                    unlocked={unlocked}
                    onRequestUnlock={() => void startDecrypt()}
                  />
                )
              ) : null}
              {message.deleted ? null : shown[message.id] ? (
                <MentionText text={shown[message.id]} members={members} />
              ) : message.messageType === 'IMAGE' || message.messageType === 'VIDEO' ? null : (
                <MentionText text="—" members={members} />
              )}
              {message.ownMessage ? <ReceiptTicks status={message.receiptStatus} /> : null}
            </div>
            {message.deleted ? null : (
              <div className="meet-bubble-tools" ref={messageMenu?.id === message.id ? messageMenuRef : undefined}>
                <button type="button" className="meet-reply-btn" onMouseDown={event => event.preventDefault()} onClick={() => beginReply(message)}>
                  Reply
                </button>
                <button
                  type="button"
                  className="meet-reply-btn"
                  aria-label="Message actions"
                  aria-haspopup="menu"
                  aria-expanded={messageMenu?.id === message.id}
                  onMouseDown={event => event.preventDefault()}
                  onClick={event => {
                    if (messageMenu?.id === message.id) {
                      setMessageMenu(null);
                      setReactPickerFor(null);
                      return;
                    }
                    openMessageMenu(message.id, event.currentTarget);
                  }}>
                  ⋮
                </button>
                {messageMenu?.id === message.id ? (
                  <div
                    className="meet-bubble-pop"
                    role="menu"
                    style={{
                      top: messageMenu.top,
                      left: messageMenu.left,
                      transform: messageMenu.up ? 'translateY(-100%)' : undefined,
                    }}>
                    {reactPickerFor === message.id ? (
                      <div className="meet-react-row">
                        {CHAT_QUICK_REACTION_EMOJIS.map(emoji => (
                          <button
                            key={emoji}
                            type="button"
                            aria-label={`React with ${emoji}`}
                            onMouseDown={event => {
                              event.preventDefault();
                              event.stopPropagation();
                              void reactTo(message, emoji);
                            }}>
                            {emoji}
                          </button>
                        ))}
                      </div>
                    ) : (
                      pipMessageActionIds(message, {allowEdit: unlocked}).map(action => (
                        <button
                          key={action}
                          type="button"
                          role="menuitem"
                          className={action === 'delete-me' || action === 'delete-everyone' ? 'danger' : undefined}
                          onMouseDown={event => {
                            event.preventDefault();
                            event.stopPropagation();
                            runMessageAction(message, action);
                          }}>
                          {pipMessageActionLabel(action)}
                        </button>
                      ))
                    )}
                  </div>
                ) : null}
              </div>
            )}
            {(reactionPatches[message.id] ?? message.reactions)?.length ? (
              <div className="meet-reactions">
                {(reactionPatches[message.id] ?? message.reactions)?.map(reaction => (
                  <button
                    key={reaction.emoji}
                    type="button"
                    className={reaction.reactedByMe ? 'mine' : undefined}
                    onMouseDown={event => event.preventDefault()}
                    onClick={() => void reactTo(message, reaction.emoji)}>
                    {reaction.emoji} {reaction.count}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        ))}
      </div>
      {editingMessage ? (
        <div className="meet-replying">
          <span>Editing</span>
          <button
            type="button"
            aria-label="Cancel edit"
            onMouseDown={event => event.preventDefault()}
            onClick={() => {
              setEditingMessage(null);
              setDraft('');
            }}>
            ×
          </button>
        </div>
      ) : null}
      {replyingTo && !editingMessage ? (
        <div className="meet-replying">
          <span>
            Replying to {resolveChatDisplayNickname(replyingTo.senderNickname, replyingTo.senderLabel)}:{' '}
            {shown[replyingTo.id] ?? preview(replyingTo, undefined)}
          </span>
          <button type="button" aria-label="Cancel reply" onMouseDown={event => event.preventDefault()} onClick={() => setReplyingTo(null)}>
            ×
          </button>
        </div>
      ) : null}
      {notice ? <p className="meet-notice">{notice}</p> : null}
      {typingLine && !editingMessage ? (
        <p className="meet-typing" aria-live="polite">
          {typingLine}
        </p>
      ) : null}
      <form
        className="meet-compose"
        onSubmit={event => {
          if (mentionChoices.length > 0) {
            event.preventDefault();
            chooseMention(safeMentionIndex);
            return;
          }
          void send(event);
        }}>
        {mentionChoices.length > 0 ? (
          <div className="meet-mentions" role="listbox" aria-label="Tag a parent">
            {mentionChoices.map((candidate, index) => (
              <button
                key={candidate.userId}
                type="button"
                role="option"
                aria-selected={index === safeMentionIndex}
                className={index === safeMentionIndex ? 'active' : undefined}
                onMouseDown={event => {
                  event.preventDefault();
                  chooseMention(index);
                }}>
                {candidate.displayName}
              </button>
            ))}
          </div>
        ) : null}
        {emojiOpen && mentionChoices.length === 0 ? (
          <div className="meet-emoji" ref={emojiRootRef} role="dialog" aria-label="Choose emoji">
            <div className="meet-emoji-modes">
              <button
                type="button"
                className={emojiMode === 'emoji' ? 'active' : undefined}
                onMouseDown={event => event.preventDefault()}
                onClick={() => setEmojiMode('emoji')}>
                Emoji
              </button>
              <button
                type="button"
                className={emojiMode === 'merged' ? 'active' : undefined}
                onMouseDown={event => event.preventDefault()}
                onClick={() => setEmojiMode('merged')}>
                Merged
              </button>
            </div>
            {emojiMode === 'emoji' ? (
              <>
                <div className="meet-emoji-tabs" role="tablist" aria-label="Emoji categories">
                  {emojiCategories.map(category => (
                    <button
                      key={category.id}
                      type="button"
                      role="tab"
                      aria-selected={category.id === emojiCategoryId}
                      aria-label={category.label}
                      className={category.id === emojiCategoryId ? 'active' : undefined}
                      onMouseDown={event => event.preventDefault()}
                      onClick={() => setEmojiCategoryId(category.id)}>
                      {category.icon}
                    </button>
                  ))}
                </div>
                <div className="meet-emoji-grid" role="tabpanel">
                  {(emojiCategories.find(category => category.id === emojiCategoryId) ?? emojiCategories[0])?.emojis.map(
                    (emoji, index) => (
                      <button
                        key={`${emojiCategoryId}-${index}-${emoji}`}
                        type="button"
                        aria-label={`Insert ${emoji}`}
                        onMouseDown={event => event.preventDefault()}
                        onClick={() => insertEmoji(emoji)}>
                        {emoji}
                      </button>
                    ),
                  )}
                </div>
              </>
            ) : (
              <div className="meet-merged-list" role="tabpanel" aria-label="Merged emoji">
                <button
                  type="button"
                  className="meet-merged-paste-toggle"
                  onMouseDown={event => event.preventDefault()}
                  onClick={() => {
                    setShowMergedPaste(open => !open);
                    setMergedPasteError(null);
                  }}>
                  {showMergedPaste ? 'Hide paste box' : 'Paste your own emoji art'}
                </button>
                {showMergedPaste ? (
                  <div className="meet-merged-paste">
                    <textarea
                      className="meet-merged-paste-input"
                      value={mergedPaste}
                      rows={8}
                      maxLength={CHAT_MESSAGE_PLAINTEXT_MAX}
                      placeholder="Paste emoji art here…"
                      spellCheck={false}
                      onMouseDown={event => event.stopPropagation()}
                      onPaste={event => {
                        const pasted = event.clipboardData.getData('text');
                        if (!pasted) {
                          return;
                        }
                        event.preventDefault();
                        setMergedPaste(artForSend(pasted));
                        setMergedPasteError(null);
                      }}
                      onChange={event =>
                        setMergedPaste(event.target.value.slice(0, CHAT_MESSAGE_PLAINTEXT_MAX))
                      }
                    />
                    <button
                      type="button"
                      className="meet-merged-paste-toggle"
                      disabled={!mergedPaste.trim()}
                      onMouseDown={event => event.preventDefault()}
                      onClick={() => setMergedPaste(artForSend(mergedPaste))}>
                      Split into rows (fix order)
                    </button>
                    {mergedPaste.trim() ? (
                      <pre className="meet-merged-paste-preview" aria-label="Preview">
                        {`Preview (${countMergedEmojiLines(mergedPaste)} lines)\n${artForSend(mergedPaste)}`}
                      </pre>
                    ) : null}
                    <input
                      value={mergedPasteName}
                      maxLength={64}
                      placeholder="Name (optional)"
                      onMouseDown={event => event.stopPropagation()}
                      onChange={event => setMergedPasteName(event.target.value)}
                    />
                    {mergedPasteError ? <p className="meet-merged-paste-error">{mergedPasteError}</p> : null}
                    <div className="meet-merged-paste-actions">
                      <button
                        type="button"
                        disabled={!mergedPaste.trim()}
                        onMouseDown={event => event.preventDefault()}
                        onClick={() => {
                          try {
                            const saved = addCustomMergedEmoji({
                              label: mergedPasteName || 'Pasted art',
                              art: artForSend(mergedPaste),
                              tags: ['my collection', 'cute'],
                            });
                            setMergedPaste('');
                            setMergedPasteName('');
                            setMergedPasteError(null);
                            setShowMergedPaste(false);
                            insertEmoji(saved.art);
                            setEmojiOpen(false);
                          } catch (error) {
                            setMergedPasteError(
                              error instanceof Error ? error.message : 'Could not save',
                            );
                          }
                        }}>
                        Save & insert
                      </button>
                      <button
                        type="button"
                        disabled={!mergedPaste.trim()}
                        onMouseDown={event => event.preventDefault()}
                        onClick={() => {
                          const art = artForSend(mergedPaste);
                          if (!art.trim()) {
                            return;
                          }
                          insertEmoji(art);
                          setEmojiOpen(false);
                        }}>
                        Insert only
                      </button>
                    </div>
                  </div>
                ) : null}
                {filterMergedEmojiItems(allMergedEmojiCollections(loadCustomMergedEmojis()), null).map(item => (
                  <button
                    key={item.id}
                    type="button"
                    className="meet-merged-item"
                    aria-label={`Insert ${item.label}`}
                    onMouseDown={event => event.preventDefault()}
                    onClick={() => {
                      insertEmoji(artForSend(item.art));
                      setEmojiOpen(false);
                    }}>
                    <span>{item.label}</span>
                    <small>{previewMergedEmojiLines(artForSend(item.art), 4, 80)}</small>
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : null}
        <textarea
          ref={composeRef}
          value={draft}
          rows={1}
          onChange={event => {
            setDraft(event.target.value);
            setCursor(event.target.selectionStart ?? event.target.value.length);
            setMentionIndex(0);
            pulseTypingFromDraft(event.target.value);
          }}
          onSelect={event => setCursor(event.currentTarget.selectionStart ?? 0)}
          onKeyDown={event => {
            if (event.key === 'Escape' && emojiOpen && mentionChoices.length === 0) {
              event.preventDefault();
              setEmojiOpen(false);
              return;
            }
            if (event.key === 'Enter' && !event.shiftKey && mentionChoices.length === 0) {
              event.preventDefault();
              if (draft.trim() && room && !sending) {
                void send(event);
              }
              return;
            }
            if (mentionChoices.length === 0) {
              return;
            }
            if (event.key === 'ArrowDown') {
              event.preventDefault();
              setMentionIndex(index => (index + 1) % mentionChoices.length);
            } else if (event.key === 'ArrowUp') {
              event.preventDefault();
              setMentionIndex(index => (index - 1 + mentionChoices.length) % mentionChoices.length);
            } else if (event.key === 'Tab') {
              event.preventDefault();
              chooseMention(safeMentionIndex);
            } else if (event.key === 'Escape' && activeMention) {
              event.preventDefault();
              setDraft(draft.slice(0, activeMention.start) + draft.slice(activeMention.end));
              setCursor(activeMention.start);
            }
          }}
          placeholder={editingMessage ? 'Edit note' : 'Type a note. Type @ to tag'}
          aria-label="Type a note"
          disabled={!room}
          maxLength={CHAT_MESSAGE_PLAINTEXT_MAX}
        />
        <button
          type="button"
          className="meet-tag"
          aria-label="Tag a parent"
          disabled={!room}
          onMouseDown={event => event.preventDefault()}
          onClick={insertAtSign}>
          @
        </button>
        <button
          ref={emojiButtonRef}
          type="button"
          className={emojiOpen ? 'meet-tag active' : 'meet-tag'}
          aria-label="Emoji"
          aria-expanded={emojiOpen}
          disabled={!room}
          onMouseDown={event => event.preventDefault()}
          onClick={() => {
            setEmojiOpen(open => {
              const next = !open;
              if (next) {
                const recent = loadRecentEmojis();
                setEmojiCategories(meetEmojiCategories(recent));
                setEmojiCategoryId(defaultEmojiCategoryId(recent));
              }
              return next;
            });
          }}>
          <EmojiIcon />
        </button>
        <button
          type="submit"
          className="meet-send"
          aria-label="Send"
          disabled={!room || sending || !draft.trim()}
          onMouseDown={event => event.preventDefault()}>
          <SendIcon />
        </button>
      </form>
      {liveCamera ? (
      <div className="meet-controls" role="toolbar" aria-label="Live view controls">
        <button
          type="button"
          className={`meet-ctl${muted ? ' hot' : ''}`}
          aria-label={muted ? 'Unmute' : 'Mute'}
          onClick={toggleMute}>
          <MicIcon off={muted} />
        </button>
        <button
          type="button"
          className={`meet-ctl${cameraHidden ? ' hot' : ''}`}
          aria-label={cameraHidden ? 'Show child camera' : 'Hide child camera'}
          onClick={() => setCameraHidden(hidden => !hidden)}>
          <CameraIcon off={cameraHidden} />
        </button>
        <button
          type="button"
          className="meet-ctl end"
          aria-label="End live view"
          onClick={() => {
            void stop();
            onClose();
          }}>
          <EndIcon />
        </button>
      </div>
      ) : null}
    </div>
  );
}

function CameraIcon({off}: {off: boolean}) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path
        fill="currentColor"
        d="M4 7h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zm12 3.2 4-2.4v8.4l-4-2.4v-3.6z"
      />
      {off ? <path d="M3 4l18 16" stroke="currentColor" strokeWidth="2" /> : null}
    </svg>
  );
}

function MentionText({text, members}: {text: string; members: ChatMemberItem[]}) {
  const parts = splitMessageText(text, collectMentionTokens(members));
  return (
    <span>
      {parts.map((part, index) =>
        part.type === 'mention' ? (
          <span key={index} className="meet-mention">
            {part.value}
          </span>
        ) : (
          <span key={index}>{part.value}</span>
        ),
      )}
    </span>
  );
}

function ReceiptTicks({status}: {status: ChatMessageItem['receiptStatus']}) {
  if (!status) {
    return null;
  }
  const label = status === 'READ' ? 'Read' : status === 'DELIVERED' ? 'Delivered' : 'Sent';
  return (
    <span className={`meet-receipt meet-receipt--${status.toLowerCase()}`} title={label} aria-label={label}>
      {status === 'SENT' ? '✓' : '✓✓'}
    </span>
  );
}

function blobUrlIn(anchor: HTMLElement | null, blob: Blob): string {
  const view = anchor?.ownerDocument.defaultView ?? window;
  return view.URL.createObjectURL(blob);
}

function revokeBlobUrl(anchor: HTMLElement | null, url: string) {
  const view = anchor?.ownerDocument.defaultView ?? window;
  view.URL.revokeObjectURL(url);
}

function MeetPipMedia({
  message,
  unlocked,
  onRequestUnlock,
}: {
  message: ChatMessageItem;
  unlocked: boolean;
  onRequestUnlock: () => void;
}) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [openedLocally, setOpenedLocally] = useState(false);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState(false);
  const anchorRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (unlocked || !blobUrl) {
      return;
    }
    revokeBlobUrl(anchorRef.current, blobUrl);
    setBlobUrl(null);
  }, [unlocked, blobUrl]);

  useEffect(() => {
    return () => {
      if (blobUrl) {
        revokeBlobUrl(anchorRef.current, blobUrl);
      }
    };
  }, [blobUrl]);

  const view = pipMediaView({
    messageType: message.messageType,
    ownMessage: message.ownMessage,
    attachmentConsumed: Boolean(message.attachmentConsumed || openedLocally),
    canViewAttachment: message.canViewAttachment,
    deleted: message.deleted,
    unlocked,
    showing: Boolean(blobUrl),
  });

  async function open() {
    if (!unlocked) {
      onRequestUnlock();
      return;
    }
    if (opening || blobUrl || !message.canViewAttachment) {
      return;
    }
    setOpening(true);
    setError(false);
    try {
      const {data} = await chatApi.viewOnceAttachment(message.roomId, message.id);
      const mime = message.attachmentMimeType ?? 'application/octet-stream';
      const url = blobUrlIn(anchorRef.current, new Blob([data], {type: mime}));
      setOpenedLocally(true);
      setBlobUrl(url);
    } catch {
      setError(true);
    } finally {
      setOpening(false);
    }
  }

  if (view.kind === 'hidden') {
    return null;
  }
  if (view.kind === 'show' && blobUrl) {
    const alt =
      message.attachmentFileName ??
      (message.messageType === 'VIDEO' ? 'Video message' : 'Image message');
    if (message.messageType === 'VIDEO') {
      return (
        <span className="meet-media-wrap" ref={anchorRef}>
          <video className="meet-media" src={blobUrl} controls playsInline aria-label={alt} />
        </span>
      );
    }
    return (
      <span className="meet-media-wrap" ref={anchorRef}>
        <img className="meet-media" src={blobUrl} alt={alt} />
      </span>
    );
  }
  if (error) {
    return (
      <span className="meet-media-wrap" ref={anchorRef}>
        <span className="meet-media-note">Could not open — it may have already been viewed</span>
      </span>
    );
  }
  if (view.kind === 'view' || view.kind === 'unlock') {
    return (
      <span className="meet-media-wrap" ref={anchorRef}>
        <button type="button" className="meet-media-btn" disabled={opening} onClick={() => void open()}>
          {opening ? 'Opening…' : view.label}
        </button>
      </span>
    );
  }
  if (view.kind === 'own' || view.kind === 'opened') {
    return (
      <span className="meet-media-wrap" ref={anchorRef}>
        <span className="meet-media-note">{view.label}</span>
      </span>
    );
  }
  return null;
}

function preview(message: ChatMessageItem, text: string | undefined): string {
  if (text) {
    return text;
  }
  if (message.messageType === 'IMAGE') {
    return 'Photo';
  }
  if (message.messageType === 'VIDEO') {
    return 'Video';
  }
  return '—';
}

function MicIcon({off}: {off: boolean}) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path
        fill="currentColor"
        d="M12 14a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v5a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V20H9v2h6v-2h-2v-2.08A7 7 0 0 0 19 11h-2z"
      />
      {off ? <path d="M4 4l16 16" stroke="currentColor" strokeWidth="2" /> : null}
    </svg>
  );
}

function EmojiIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.75" />
      <path d="M8 14s1.5 2 4 2 4-2 4-2" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
      <circle cx="9" cy="10" r="1" fill="currentColor" />
      <circle cx="15" cy="10" r="1" fill="currentColor" />
    </svg>
  );
}

function SendIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path fill="currentColor" d="M4 12l16-8-6 16-2-6-8-2z" />
    </svg>
  );
}

function EndIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path
        fill="currentColor"
        d="M6.6 10.8c1.4 1 3.3 1.7 5.4 1.7s4-.7 5.4-1.7l2.1 2.1c-2 1.6-4.6 2.6-7.5 2.6s-5.5-1-7.5-2.6l2.1-2.1z"
      />
    </svg>
  );
}

const MEET_PIP_CSS = `
.meet-pip { height: 100%; display: flex; flex-direction: column; background: #202124; color: #e8eaed; font-family: Roboto, system-ui, sans-serif; }
.meet-stage { display: flex; justify-content: center; box-sizing: border-box; padding: 8px 12px 0; height: calc(100% - 220px); min-height: 240px; }
.meet-stage.hidden { display: none; }
.meet-video-card, .meet-video-card.turned { position: relative; width: 100%; height: 100%; max-width: none; aspect-ratio: auto; border-radius: 12px; overflow: hidden; background: #000; }
.meet-video-card video { width: 100%; height: 100%; object-fit: contain; background: #000; }
.meet-name { position: absolute; left: 10px; bottom: 8px; font-size: 13px; text-shadow: 0 1px 2px #000; }
.meet-video-actions { position: absolute; top: 8px; right: 8px; display: flex; gap: 6px; }
.meet-rot { width: 32px; height: 32px; border: 0; border-radius: 50%; background: rgba(32,33,36,.75); color: #fff; cursor: pointer; font-size: 16px; }
.meet-titlebar { display: flex; align-items: center; gap: 8px; padding: 6px 16px 2px; }
.meet-titlebar.draggable { position: relative; cursor: ns-resize; touch-action: none; padding-top: 12px; }
.meet-titlebar.draggable::before { content: ''; position: absolute; top: 4px; left: 50%; width: 36px; height: 4px; margin-left: -18px; border-radius: 999px; background: #5f6368; }
.meet-titlebar.draggable button, .meet-titlebar.draggable .meet-menu { cursor: pointer; touch-action: auto; }
.meet-title { flex: 1; min-width: 0; font-size: 20px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.meet-menu { position: relative; }
.meet-more { width: 32px; height: 32px; border: 0; border-radius: 50%; background: transparent; color: #e8eaed; cursor: pointer; display: grid; place-items: center; }
.meet-more:disabled { color: #5f6368; cursor: default; }
.meet-menu-pop { position: absolute; right: 0; top: calc(100% + 4px); z-index: 5; min-width: 220px; max-height: 240px; overflow-y: auto; display: flex; flex-direction: column; padding: 6px; border-radius: 12px; background: #303134; box-shadow: 0 4px 16px rgba(0,0,0,0.45); }
.meet-menu-pop button { border: 0; background: transparent; color: #e8eaed; text-align: left; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 13px; }
.meet-menu-pop button:hover { background: #3c4043; }
.meet-menu-pop button.current { color: #8ab4f8; }
.meet-menu-pop button.danger { color: #f28b82; }
.meet-min { width: 32px; height: 32px; border: 0; background: transparent; color: #e8eaed; font-size: 20px; line-height: 1; cursor: pointer; }
.meet-pip--dock { height: 100%; display: flex; align-items: center; background: #fff; color: #1c2438; }
.meet-dock-hold { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.meet-dock-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; font: inherit; }
.meet-dock-dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; flex-shrink: 0; }
.meet-dock-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 700; }
.meet-dock-live { flex-shrink: 0; font-size: 11px; font-weight: 700; letter-spacing: 0.04em; color: #22c55e; }
.meet-dock-close { border: 0; background: transparent; color: #5f6368; font-size: 22px; line-height: 1; padding: 0 12px; cursor: pointer; }
.meet-confirm { margin: 8px 16px 0; padding: 10px; border-radius: 12px; background: #303134; }
.meet-confirm p { margin: 0 0 8px; font-size: 13px; line-height: 1.35; }
.meet-confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
.meet-confirm button { border: 0; border-radius: 8px; background: #3c4043; color: #e8eaed; padding: 6px 10px; cursor: pointer; font-size: 13px; }
.meet-confirm button.danger { background: #f28b82; color: #202124; }
.meet-lock { border: 0; background: #3c4043; color: #e8eaed; border-radius: 999px; padding: 6px 10px; font-size: 12px; cursor: pointer; }
.meet-pin { display: flex; gap: 8px; margin: 8px 16px 0; }
.meet-pin input { flex: 1; border: 0; border-radius: 8px; background: #3c4043; color: inherit; padding: 8px 10px; }
.meet-pin button { border: 0; border-radius: 8px; background: #8ab4f8; color: #202124; padding: 8px 12px; cursor: pointer; }
.meet-close { border: 0; background: transparent; color: #e8eaed; font-size: 26px; line-height: 1; cursor: pointer; padding: 0 4px; }
.meet-log { flex: 1; min-height: 0; overflow: auto; display: flex; flex-direction: column; gap: 8px; padding: 12px 16px; }
.meet-empty, .meet-notice { margin: 0; color: #9aa0a6; font-size: 13px; text-align: center; }
.meet-notice { padding: 0 16px 4px; }
.meet-bubble { align-self: flex-start; max-width: 86%; padding: 8px 12px; border-radius: 12px; background: #3c4043; font-size: 14px; line-height: 1.35; white-space: pre-wrap; word-break: break-word; }
.meet-bubble.own { align-self: flex-end; background: #394457; }
.meet-bubble-text { display: block; white-space: pre-wrap; }
.meet-media-wrap { display: block; }
.meet-media { display: block; max-width: 100%; max-height: 180px; margin-top: 4px; border-radius: 8px; object-fit: contain; background: #000; }
.meet-media-note { display: block; color: #9aa0a6; font-size: 12px; }
.meet-media-btn { display: block; margin-top: 4px; border: 1px dashed #8ab4f8; border-radius: 8px; background: transparent; color: #8ab4f8; padding: 6px 8px; cursor: pointer; font-size: 12px; text-align: left; }
.meet-media-btn:disabled { opacity: 0.6; cursor: wait; }
.meet-reply { display: block; margin-bottom: 4px; padding-left: 6px; border-left: 2px solid #8ab4f8; color: #9aa0a6; font-size: 12px; }
.meet-reply-btn { border: 0; background: transparent; color: #8ab4f8; font-size: 11px; cursor: pointer; padding: 0; }
.meet-bubble-tools { display: flex; align-items: center; gap: 10px; margin-top: 4px; }
.meet-bubble-pop { position: fixed; z-index: 6; min-width: 180px; display: flex; flex-direction: column; padding: 6px; border-radius: 12px; background: #303134; box-shadow: 0 4px 16px rgba(0,0,0,.45); }
.meet-bubble-pop button { border: 0; background: transparent; color: #e8eaed; text-align: left; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 13px; }
.meet-bubble-pop button.danger { color: #f28b82; }
.meet-react-row { display: flex; gap: 2px; }
.meet-react-row button { width: 32px; height: 32px; padding: 0; border-radius: 50%; text-align: center; font-size: 18px; }
.meet-reactions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.meet-reactions button { border: 0; border-radius: 999px; background: #303134; color: #e8eaed; font-size: 12px; padding: 2px 6px; cursor: pointer; }
.meet-reactions button.mine { outline: 1px solid #8ab4f8; }
.meet-replying { display: flex; align-items: center; gap: 8px; margin: 0 16px 6px; padding: 6px 8px; border-radius: 8px; background: #303134; font-size: 12px; color: #e8eaed; }
.meet-replying span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meet-replying button { border: 0; background: transparent; color: #e8eaed; cursor: pointer; font-size: 18px; line-height: 1; }
.meet-deleted { display: block; font-size: 11px; font-weight: 700; color: #f6c453; margin-bottom: 2px; }
.meet-receipt { margin-left: 6px; font-size: 12px; letter-spacing: -1px; }
.meet-receipt--sent, .meet-receipt--delivered { color: #8ab4f8; }
.meet-receipt--read { color: #22c55e; }
.meet-bubble-label { display: block; font-size: 11px; color: #9aa0a6; margin-bottom: 2px; }
.meet-compose { position: relative; display: flex; align-items: flex-end; gap: 8px; margin: 8px 16px; padding: 6px 8px 6px 16px; background: #3c4043; border-radius: 24px; }
.meet-typing { margin: 0 22px 0; min-height: 16px; font-size: 12px; font-weight: 600; font-style: italic; color: #8ab4f8; }
.meet-emoji { position: absolute; left: 0; right: 0; bottom: calc(100% + 6px); z-index: 4; display: flex; flex-direction: column; gap: 6px; max-height: 260px; padding: 8px; border-radius: 12px; background: #303134; box-shadow: 0 4px 16px rgba(0,0,0,0.45); }
.meet-emoji-modes { display: flex; gap: 6px; }
.meet-emoji-modes button { border: 0; border-radius: 999px; background: #3c4043; color: #e8eaed; font-size: 12px; font-weight: 600; padding: 4px 10px; cursor: pointer; }
.meet-emoji-modes button.active { background: #5f6368; }
.meet-emoji-tabs { display: flex; gap: 4px; overflow-x: auto; }
.meet-emoji-tabs button { flex-shrink: 0; width: 32px; height: 32px; border: 0; border-radius: 8px; background: transparent; font-size: 18px; cursor: pointer; }
.meet-emoji-tabs button.active, .meet-emoji-tabs button:hover { background: #3c4043; }
.meet-emoji-grid { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 2px; overflow-y: auto; max-height: 160px; }
.meet-emoji-grid button { border: 0; border-radius: 8px; background: transparent; font-size: 20px; height: 32px; cursor: pointer; }
.meet-emoji-grid button:hover { background: #3c4043; }
.meet-merged-list { display: flex; flex-direction: column; gap: 4px; overflow-y: auto; max-height: 220px; }
.meet-merged-paste-toggle { border: 1px dashed #5f6368; border-radius: 8px; background: #3c4043; color: #e8eaed; font-size: 12px; font-weight: 700; padding: 8px; cursor: pointer; text-align: left; }
.meet-merged-paste { display: flex; flex-direction: column; gap: 6px; }
.meet-merged-paste textarea, .meet-merged-paste input { width: 100%; box-sizing: border-box; border: 0; border-radius: 8px; background: #202124; color: #e8eaed; font: inherit; font-size: 12px; padding: 8px; resize: vertical; }
.meet-merged-paste-input { white-space: pre; overflow: auto; min-height: 120px; max-height: 200px; font-family: ui-monospace, Menlo, monospace; line-height: 1.35; }
.meet-merged-paste-preview { margin: 0; padding: 6px 8px; border-radius: 8px; background: #202124; color: #9aa0a6; font-size: 11px; line-height: 1.35; white-space: pre; max-height: min(240px, 38dvh); overflow: auto; font-family: ui-monospace, Menlo, monospace; }
.meet-merged-item small { font-size: 11px; color: #9aa0a6; white-space: pre-wrap; max-width: 100%; line-height: 1.3; }
.meet-merged-paste-actions { display: flex; gap: 6px; }
.meet-merged-paste-actions button { border: 0; border-radius: 8px; background: #8ab4f8; color: #202124; font-size: 12px; font-weight: 700; padding: 6px 8px; cursor: pointer; }
.meet-merged-paste-actions button:disabled { opacity: 0.5; cursor: default; }
.meet-merged-paste-error { margin: 0; font-size: 11px; color: #f28b82; }
.meet-merged-item { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; border: 0; border-radius: 8px; background: #3c4043; color: #e8eaed; text-align: left; padding: 6px 8px; cursor: pointer; }
.meet-merged-item span { font-size: 12px; font-weight: 700; }
.meet-merged-item small { font-size: 11px; color: #9aa0a6; white-space: pre-wrap; max-width: 100%; line-height: 1.3; }
.meet-mentions { position: absolute; left: 0; right: 40px; bottom: calc(100% + 6px); display: flex; flex-direction: column; max-height: 140px; overflow-y: auto; border-radius: 12px; background: #303134; box-shadow: 0 4px 16px rgba(0,0,0,0.4); z-index: 2; }
.meet-mentions button { border: 0; background: transparent; color: #e8eaed; text-align: left; padding: 8px 12px; cursor: pointer; font-size: 13px; }
.meet-mentions button.active, .meet-mentions button:hover { background: #3c4043; }
.meet-mention { color: #8ab4f8; font-weight: 600; }
.meet-compose input, .meet-compose textarea { flex: 1; min-width: 0; border: 0; background: transparent; color: inherit; font-size: 14px; outline: none; font-family: inherit; resize: none; max-height: 120px; line-height: 1.35; }
.meet-send { width: 36px; height: 36px; border: 0; border-radius: 50%; background: transparent; color: #8ab4f8; cursor: pointer; display: grid; place-items: center; }
.meet-tag { width: 36px; height: 36px; border: 0; border-radius: 50%; background: transparent; color: #e8eaed; cursor: pointer; font-size: 16px; font-weight: 700; display: grid; place-items: center; }
.meet-tag.active { background: #5f6368; }
.meet-tag:disabled { color: #5f6368; cursor: default; }
.meet-send:disabled { color: #5f6368; cursor: default; }
.meet-controls { display: flex; justify-content: center; gap: 18px; padding: 10px 12px 16px; }
.meet-ctl { width: 52px; height: 52px; border: 0; border-radius: 50%; background: #3c4043; color: #fff; cursor: pointer; display: grid; place-items: center; }
.meet-ctl.hot, .meet-ctl.end { background: #ea4335; }
`;
