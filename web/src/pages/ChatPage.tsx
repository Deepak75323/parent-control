import {Fragment, type FormEvent, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import axios from 'axios';
import {chatApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {encodeChatMessagePlaintext} from '../lib/chatMessageObfuscation';
import {groupMessagesByDate} from '../lib/chatMessageGrouping';
import {resolveOwnSenderUserId, withResolvedOwnership} from '../lib/chatMessageOwnership';
import {withMonotonicReceiptStatuses} from '../lib/chatReceiptMonotonic';
import {
  advanceReadCursor,
  isAfterReadCursor,
  mergeChatUnreadFlag,
  previousMessageId,
  unreadAfterCursor,
} from '../lib/chatUnreadMerge';
import {resolveChatDisplayNickname} from '../lib/chatDisplayNickname';
import {validateChatMediaFile} from '../lib/chatMediaUpload';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {Avatar} from '../components/Avatar';
import {ChatMessageMedia} from '../components/ChatMessageMedia';
import {ChatInviteBar} from '../components/chat/ChatInviteBar';
import {ChatFileDropZone} from '../components/chat/ChatFileDropZone';
import {ChatHeader} from '../components/chat/ChatHeader';
import {ChatRoomsPanel} from '../components/chat/ChatRoomsPanel';
import {ChatWorkspaceLayout} from '../components/chat/ChatWorkspaceLayout';
import {useChatWorkspaceLayout} from '../hooks/useChatWorkspaceLayout';
import {useConfirmDialog} from '../hooks/useConfirmDialog';
import {DateSeparator} from '../components/chat/DateSeparator';
import {UnreadSeparator} from '../components/chat/UnreadSeparator';
import {EmptyChatState} from '../components/chat/EmptyChatState';
import {MessageBubble} from '../components/chat/MessageBubble';
import {ComposerReplyBar} from '../components/chat/ChatMessageReplyQuote';
import {EditMessageComposer, MessageComposer} from '../components/chat/MessageComposer';
import {collectMentionTokens} from '../lib/chatMentions';
import {replyPreviewFromMessage} from '../lib/chatReplyPreview';
import {
  flashChatMessageHighlight,
  scrollChatMessageIntoView,
} from '../lib/chatScrollToMessage';
import {ChatMessageText} from '../components/chat/ChatMessageText';
import type {ChatInviteItem, ChatMemberItem, ChatMessageItem, ChatRoomSummary} from '../types/api';

export function ChatPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const workspace = useChatWorkspaceLayout();
  const profile = useAuthStore(s => s.profile);
  const email = useAuthStore(s => s.email);
  const [rooms, setRooms] = useState<ChatRoomSummary[]>([]);
  const [invites, setInvites] = useState<ChatInviteItem[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState<string | null>(null);
  const [apiMessages, setApiMessages] = useState<ChatMessageItem[]>([]);
  const [messagesLoadedAt, setMessagesLoadedAt] = useState<Record<string, number>>({});
  const [draft, setDraft] = useState('');
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [pendingPreviewUrl, setPendingPreviewUrl] = useState<string | null>(null);
  const [newRoomName, setNewRoomName] = useState('');
  const [inviteIdentifier, setInviteIdentifier] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [replyingTo, setReplyingTo] = useState<ChatMessageItem | null>(null);
  const [roomMembers, setRoomMembers] = useState<ChatMemberItem[]>([]);
  const [showInvite, setShowInvite] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesScrollRef = useRef<HTMLDivElement>(null);
  const pendingScrollMessageIdRef = useRef<string | null>(null);
  const highlightClearRef = useRef<(() => void) | null>(null);
  const composerFileInputRef = useRef<HTMLInputElement>(null);
  const composerTextareaRef = useRef<HTMLTextAreaElement>(null);
  const initialRoomLoadRef = useRef(true);
  const messagesLoadGenRef = useRef(0);
  const initialScrollDoneRef = useRef<string | null>(null);
  const roomsDrawerDismissedRef = useRef(false);
  const [atBottom, setAtBottom] = useState(true);
  const {confirm, dialog: confirmDialog} = useConfirmDialog(busy);

  const ownDisplayName = useMemo(
    () =>
      resolveChatDisplayNickname(
        profile?.chatNickname,
        email ?? `${profile?.firstName ?? ''} ${profile?.lastName ?? ''}`.trim(),
      ),
    [profile, email],
  );
  const ownSeed = profile?.id ?? email ?? 'self';

  const chatRoomsEpoch = useRealtimeStore(s => s.chatRoomsEpoch);
  const markChatRoomRemoved = useRealtimeStore(s => s.markChatRoomRemoved);
  const unmarkChatRoomRemoved = useRealtimeStore(s => s.unmarkChatRoomRemoved);
  const pruneLiveChatMessages = useRealtimeStore(s => s.pruneLiveChatMessages);
  const clearLiveChatMessagesForRoom = useRealtimeStore(s => s.clearLiveChatMessagesForRoom);
  const clearPersonalChatForRoom = useRealtimeStore(s => s.clearPersonalChatForRoom);
  const syncPersonalChatClearedAt = useRealtimeStore(s => s.syncPersonalChatClearedAt);
  const syncLiveChatMessagesFromApi = useRealtimeStore(s => s.syncLiveChatMessagesFromApi);
  const chatInvitesEpoch = useRealtimeStore(s => s.chatInvitesEpoch);
  const liveChatMessages = useRealtimeStore(s => s.liveChatMessages);
  const deletedChatMessageIds = useRealtimeStore(s => s.deletedChatMessageIds);
  const chatClearedEpoch = useRealtimeStore(s => s.chatClearedEpoch);
  const chatClearedAt = useRealtimeStore(s => s.chatClearedAt);
  const personalChatClearedAt = useRealtimeStore(s => s.personalChatClearedAt);
  const removeLiveChatMessage = useRealtimeStore(s => s.removeLiveChatMessage);
  const receiptPatches = useRealtimeStore(s => s.receiptPatches);
  const reactionPatches = useRealtimeStore(s => s.reactionPatches);
  const deliveredMarkedRef = useRef<Set<string>>(new Set());
  const lastReadMarkedRef = useRef<string | null>(null);
  const catchUpReadRef = useRef<() => void>(() => undefined);
  const readMarkEpochRef = useRef(0);
  /** Block auto mark-read until the user scrolls (keeps unread divider visible). */
  const suppressMarkReadRef = useRef(false);
  /** WhatsApp read cursor — source of truth for unread UI. */
  const [lastReadMessageId, setLastReadMessageId] = useState<string | null>(null);
  const lastReadMessageIdRef = useRef<string | null>(null);
  /** Frozen divider while suppress is on so short chats still show the line. */
  const [unreadDivider, setUnreadDivider] = useState<{
    firstId: string;
    count: number;
  } | null>(null);

  const setReadCursor = (messageId: string | null) => {
    lastReadMessageIdRef.current = messageId;
    setLastReadMessageId(messageId);
  };

  const selectedRoom = useMemo(
    () => rooms.find(room => room.id === selectedRoomId) ?? null,
    [rooms, selectedRoomId],
  );

  const ownSenderUserId = useMemo(
    () =>
      selectedRoomId
        ? resolveOwnSenderUserId(apiMessages.filter(message => message.roomId === selectedRoomId))
        : null,
    [apiMessages, selectedRoomId],
  );

  const messages = useMemo(() => {
    if (!selectedRoomId) {
      return [];
    }
    const loadedAt = messagesLoadedAt[selectedRoomId] ?? 0;
    const globalClearedAt = chatClearedAt[selectedRoomId];
    const personalClearedAt = personalChatClearedAt[selectedRoomId];
    const byId = new Map<string, ChatMessageItem>();

    for (const message of apiMessages) {
      if (message.roomId !== selectedRoomId) {
        continue;
      }
      if (deletedChatMessageIds[message.id]) {
        continue;
      }
      const createdMs = new Date(message.createdAt).getTime();
      if (globalClearedAt && createdMs <= globalClearedAt) {
        continue;
      }
      if (personalClearedAt && createdMs <= personalClearedAt) {
        continue;
      }
      byId.set(message.id, message);
    }

    if (loadedAt > 0) {
      for (const message of liveChatMessages) {
        if (message.roomId !== selectedRoomId) {
          continue;
        }
        if (deletedChatMessageIds[message.id]) {
          continue;
        }
        const createdMs = new Date(message.createdAt).getTime();
        if (globalClearedAt && createdMs <= globalClearedAt) {
          continue;
        }
        if (personalClearedAt && createdMs <= personalClearedAt) {
          continue;
        }
        if (createdMs <= loadedAt && !byId.has(message.id)) {
          continue;
        }
        const existing = byId.get(message.id);
        byId.set(
          message.id,
          existing
            ? {
                ...existing,
                ...message,
                unread: mergeChatUnreadFlag(existing.unread, message.unread),
              }
            : message,
        );
      }
    }

    return withMonotonicReceiptStatuses(
      Array.from(byId.values())
        .map(message => withResolvedOwnership(message, ownSenderUserId))
        .map(message => {
          const patch = receiptPatches[message.id];
          const reactions = reactionPatches[message.id] ?? message.reactions;
          const withReceipt = patch ? {...message, receiptStatus: patch} : message;
          return reactions !== message.reactions ? {...withReceipt, reactions} : withReceipt;
        })
        .sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
        ),
    );
  }, [
    apiMessages,
    liveChatMessages,
    selectedRoomId,
    deletedChatMessageIds,
    chatClearedAt,
    personalChatClearedAt,
    messagesLoadedAt,
    receiptPatches,
    reactionPatches,
    ownSenderUserId,
  ]);

  const visibleMessages = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const scoped = query
      ? messages.filter(message => message.body?.toLowerCase().includes(query))
      : messages;
    // Derive unread from the read cursor (not a mutable counter).
    return scoped.map(message => ({
      ...message,
      unread:
        !message.ownMessage &&
        isAfterReadCursor(message, lastReadMessageId, scoped),
    }));
  }, [messages, searchQuery, lastReadMessageId]);

  const unreadMessages = useMemo(
    () => unreadAfterCursor(visibleMessages, lastReadMessageId),
    [visibleMessages, lastReadMessageId],
  );
  const liveUnreadCount = unreadMessages.length;
  const unreadCount = Math.max(liveUnreadCount, unreadDivider?.count ?? 0);
  const firstUnreadId = unreadDivider?.firstId ?? unreadMessages[0]?.id ?? null;

  const messageGroups = useMemo(
    () => groupMessagesByDate(visibleMessages),
    [visibleMessages],
  );

  const editingMessage = useMemo(
    () => messages.find(message => message.id === editingMessageId) ?? null,
    [messages, editingMessageId],
  );

  const selectRoom = useCallback(
    (roomId: string) => {
      setSelectedRoomId(roomId);
      setReplyingTo(null);
      if (workspace.isMobile) {
        workspace.closeRoomsDrawer();
      }
    },
    [workspace.isMobile, workspace.closeRoomsDrawer],
  );

  const handleMobileBackFromChat = useCallback(() => {
    setSelectedRoomId(null);
    setShowInvite(false);
    setSearchOpen(false);
    setReplyingTo(null);
    if (workspace.isMobile) {
      roomsDrawerDismissedRef.current = false;
      workspace.openRoomsDrawer();
    }
  }, [workspace.isMobile, workspace.openRoomsDrawer]);

  const handleCloseRoomsDrawer = useCallback(() => {
    roomsDrawerDismissedRef.current = true;
    workspace.closeRoomsDrawer();
  }, [workspace.closeRoomsDrawer]);

  function handleRemoveRoom(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room) {
      return;
    }
    if (room.owner) {
      void handleDeleteRoom(roomId);
      return;
    }
    void handleLeaveRoom(roomId);
  }

  useEffect(() => {
    if (!workspace.isMobile || !location.pathname.endsWith('/chat')) {
      return;
    }
    if (selectedRoomId) {
      workspace.closeRoomsDrawer();
    }
  }, [workspace.isMobile, selectedRoomId, workspace.closeRoomsDrawer]);

  useEffect(() => {
    if (!workspace.isMobile || !location.pathname.endsWith('/chat')) {
      return;
    }
    if (roomsDrawerDismissedRef.current || selectedRoomId) {
      return;
    }
    workspace.openRoomsDrawer();
  }, [location.pathname, workspace.isMobile, selectedRoomId, workspace.openRoomsDrawer]);

  useEffect(() => {
    if (!location.pathname.endsWith('/chat')) {
      return;
    }
    roomsDrawerDismissedRef.current = false;
  }, [location.pathname]);

  const loadRooms = useCallback(async () => {
    try {
      const {data} = await chatApi.listRooms();
      const apiRooms = data.data ?? [];
      const removed = useRealtimeStore.getState().removedChatRoomIds;
      const next = apiRooms.filter(room => !removed[room.id]);
      for (const id of Object.keys(removed)) {
        if (!apiRooms.some(room => room.id === id)) {
          unmarkChatRoomRemoved(id);
        }
      }
      pruneLiveChatMessages(next.map(room => room.id));
      for (const room of next) {
        if (room.chatClearedAt) {
          syncPersonalChatClearedAt(room.id, new Date(room.chatClearedAt).getTime());
        }
      }
      setRooms(next);
      setSelectedRoomId(prev => {
        if (prev && next.some(room => room.id === prev)) {
          return prev;
        }
        if (prev && !next.some(room => room.id === prev)) {
          return next[0]?.id ?? null;
        }
        if (!prev && initialRoomLoadRef.current && next.length > 0) {
          initialRoomLoadRef.current = false;
          if (!window.matchMedia('(max-width: 900px)').matches) {
            return next[0]?.id ?? null;
          }
          return prev;
        }
        return prev;
      });
      if (initialRoomLoadRef.current && next.length === 0) {
        initialRoomLoadRef.current = false;
      }
      setApiMessages(prev => {
        if (prev.length === 0) {
          return prev;
        }
        const allowed = new Set(next.map(room => room.id));
        return prev.filter(message => allowed.has(message.roomId));
      });
    } catch {
      setError('Failed to load chat rooms');
    }
  }, [pruneLiveChatMessages, unmarkChatRoomRemoved, syncPersonalChatClearedAt]);

  const mentionTokens = useMemo(() => collectMentionTokens(roomMembers), [roomMembers]);

  const mentionableMembers = useMemo(() => {
    if (!profile?.id) {
      return roomMembers;
    }
    return roomMembers.filter(member => member.userId !== profile.id);
  }, [roomMembers, profile?.id]);

  const loadRoomMembers = useCallback(async (roomId: string) => {
    try {
      const {data} = await chatApi.listMembers(roomId);
      setRoomMembers(data.data ?? []);
    } catch {
      setRoomMembers([]);
    }
  }, []);

  const loadInvites = useCallback(async () => {
    try {
      const {data} = await chatApi.listPendingInvites();
      setInvites(data.data ?? []);
    } catch {
      setError('Failed to load invites');
    }
  }, []);

  const loadMessages = useCallback(
    async (roomId: string) => {
      const loadGen = ++messagesLoadGenRef.current;
      try {
        const {data} = await chatApi.listMessages(roomId);
        if (loadGen !== messagesLoadGenRef.current) {
          return;
        }
        const roomMessages = data.data ?? [];
        syncLiveChatMessagesFromApi(roomId, roomMessages);
        setApiMessages(prev => [...prev.filter(message => message.roomId !== roomId), ...roomMessages]);
        setMessagesLoadedAt(prev => ({...prev, [roomId]: Date.now()}));
      } catch {
        if (loadGen !== messagesLoadGenRef.current) {
          return;
        }
        setError('Failed to load messages');
      }
    },
    [syncLiveChatMessagesFromApi],
  );

  useEffect(() => {
    initialRoomLoadRef.current = true;
    void loadRooms();
    void loadInvites();
  }, [loadRooms, loadInvites, chatRoomsEpoch, location.pathname]);

  useEffect(() => {
    void loadInvites();
  }, [loadInvites, chatInvitesEpoch]);

  useEffect(() => {
    if (!selectedRoomId) {
      return;
    }
    setEditingMessageId(null);
    setEditDraft('');
    setShowInvite(false);
    setSearchOpen(false);
    setSearchQuery('');
    setMessagesLoadedAt(prev => {
      const next = {...prev};
      delete next[selectedRoomId];
      return next;
    });
    setApiMessages(prev => prev.filter(message => message.roomId !== selectedRoomId));
    void loadMessages(selectedRoomId);
    void loadRoomMembers(selectedRoomId);
  }, [selectedRoomId, loadMessages, loadRoomMembers]);

  useEffect(() => {
    if (!selectedRoomId) {
      return;
    }
    void loadRoomMembers(selectedRoomId);
  }, [selectedRoomId, loadRoomMembers, chatRoomsEpoch]);

  useEffect(() => {
    if (!selectedRoomId || !chatClearedEpoch[selectedRoomId]) {
      return;
    }
    setApiMessages(prev => prev.filter(message => message.roomId !== selectedRoomId));
    setMessagesLoadedAt(prev => ({...prev, [selectedRoomId]: Date.now()}));
    void loadMessages(selectedRoomId);
  }, [chatClearedEpoch, selectedRoomId, loadMessages]);

  const patchMessage = useCallback((messageId: string, patch: Partial<ChatMessageItem>) => {
    setApiMessages(prev =>
      prev.map(message => (message.id === messageId ? {...message, ...patch} : message)),
    );
  }, []);

  useEffect(() => {
    setApiMessages(prev => {
      const next = prev.filter(message => !deletedChatMessageIds[message.id]);
      return next.length === prev.length ? prev : next;
    });
  }, [deletedChatMessageIds]);

  useEffect(() => {
    deliveredMarkedRef.current.clear();
    lastReadMarkedRef.current = null;
    initialScrollDoneRef.current = null;
    suppressMarkReadRef.current = false;
    setUnreadDivider(null);
    setAtBottom(false);
    const roomCursor =
      rooms.find(room => room.id === selectedRoomId)?.lastReadMessageId ?? null;
    setReadCursor(roomCursor ?? null);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when switching rooms
  }, [selectedRoomId]);

  useEffect(() => {
    const root = messagesScrollRef.current;
    const end = messagesEndRef.current;
    if (!root || !end || !selectedRoomId) {
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => setAtBottom(entry?.isIntersecting ?? false),
      {root, threshold: 0.25},
    );
    observer.observe(end);
    return () => observer.disconnect();
  }, [selectedRoomId, visibleMessages.length]);

  useEffect(() => {
    if (!selectedRoomId || visibleMessages.length === 0 || searchQuery.trim()) {
      return;
    }
    if (initialScrollDoneRef.current === selectedRoomId) {
      return;
    }
    if (!messagesLoadedAt[selectedRoomId]) {
      return;
    }

    // Seed cursor from raw API unread flags (before cursor overlay).
    const roomApiMessages = apiMessages.filter(message => message.roomId === selectedRoomId);
    const serverFirstUnread = roomApiMessages.find(
      message => !message.ownMessage && message.unread,
    );
    if (serverFirstUnread) {
      const prev = previousMessageId(roomApiMessages, serverFirstUnread.id);
      setReadCursor(prev);
    } else if (!lastReadMessageIdRef.current) {
      const room = rooms.find(entry => entry.id === selectedRoomId);
      if ((room?.unreadCount ?? 0) > 0 && room?.lastReadMessageId) {
        setReadCursor(room.lastReadMessageId);
      }
    }

    initialScrollDoneRef.current = selectedRoomId;
    const cursor = lastReadMessageIdRef.current;
    const openedUnread = unreadAfterCursor(
      roomApiMessages.map(message => ({
        ...message,
        unread:
          !message.ownMessage &&
          (cursor
            ? isAfterReadCursor(message, cursor, roomApiMessages)
            : Boolean(message.unread)),
      })),
      cursor,
    );
    const firstUnread = openedUnread[0];
    requestAnimationFrame(() => {
      if (firstUnread) {
        suppressMarkReadRef.current = true;
        setUnreadDivider({firstId: firstUnread.id, count: openedUnread.length});
        document
          .getElementById(`chat-message-${firstUnread.id}`)
          ?.scrollIntoView({block: 'center', behavior: 'auto'});
        setAtBottom(false);
      } else {
        suppressMarkReadRef.current = false;
        setUnreadDivider(null);
        messagesEndRef.current?.scrollIntoView({behavior: 'auto'});
        setAtBottom(true);
      }
    });
  }, [selectedRoomId, visibleMessages, messagesLoadedAt, searchQuery, apiMessages, rooms]);

  useEffect(() => {
    if (!selectedRoomId || visibleMessages.length === 0) {
      return;
    }
    const pending = visibleMessages
      .filter(message => !message.ownMessage && !deliveredMarkedRef.current.has(message.id))
      .map(message => message.id);
    if (pending.length === 0) {
      return;
    }
    pending.forEach(id => deliveredMarkedRef.current.add(id));
    void chatApi.markDelivered(selectedRoomId, pending).catch(() => undefined);
  }, [selectedRoomId, visibleMessages]);

  // Keep frozen divider count in sync when more messages arrive while still unread.
  useEffect(() => {
    if (!unreadDivider || !suppressMarkReadRef.current) {
      return;
    }
    if (liveUnreadCount > unreadDivider.count) {
      setUnreadDivider(prev =>
        prev ? {...prev, count: liveUnreadCount} : prev,
      );
    }
  }, [liveUnreadCount, unreadDivider]);

  // WhatsApp: at the bottom ⇒ advance read cursor — but not until the user scrolls
  // after opening onto unread (otherwise short chats never show the divider).
  useEffect(() => {
    if (!selectedRoomId || !atBottom || visibleMessages.length === 0) {
      return;
    }
    if (initialScrollDoneRef.current !== selectedRoomId) {
      return;
    }
    if (suppressMarkReadRef.current) {
      return;
    }
    const lastIncoming = [...visibleMessages].reverse().find(message => !message.ownMessage);
    if (!lastIncoming || lastIncoming.id === lastReadMarkedRef.current) {
      return;
    }
    const nextCursor = advanceReadCursor(
      lastReadMessageIdRef.current,
      lastIncoming.id,
      visibleMessages,
    );
    if (nextCursor === lastReadMessageIdRef.current && lastIncoming.id === lastReadMarkedRef.current) {
      return;
    }
    lastReadMarkedRef.current = lastIncoming.id;
    const epoch = readMarkEpochRef.current;
    setReadCursor(nextCursor);
    setUnreadDivider(null);
    void chatApi
      .markRead(selectedRoomId, lastIncoming.id)
      .then(() => {
        if (epoch !== readMarkEpochRef.current) {
          return;
        }
        useRealtimeStore.getState().markRoomIncomingRead(selectedRoomId);
        setApiMessages(prev =>
          prev.map(message =>
            message.roomId === selectedRoomId && !message.ownMessage
              ? {...message, unread: false}
              : message,
          ),
        );
        setRooms(prev =>
          prev.map(room =>
            room.id === selectedRoomId
              ? {...room, lastReadMessageId: lastIncoming.id, unreadCount: 0}
              : room,
          ),
        );
      })
      .catch(() => undefined);
  }, [selectedRoomId, atBottom, visibleMessages]);

  useEffect(() => {
    if (!atBottom || searchQuery.trim()) {
      return;
    }
    if (initialScrollDoneRef.current !== selectedRoomId) {
      return;
    }
    if (suppressMarkReadRef.current) {
      return;
    }
    messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
  }, [visibleMessages.length, atBottom, searchQuery, selectedRoomId]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || unreadAfterCursor(visibleMessages, lastReadMessageIdRef.current).length === 0) {
        return;
      }
      catchUpReadRef.current();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [visibleMessages]);

  useEffect(() => {
    if (!pendingFile) {
      setPendingPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(pendingFile);
    setPendingPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [pendingFile]);

  function clearPendingFile() {
    setPendingFile(null);
    if (composerFileInputRef.current) {
      composerFileInputRef.current.value = '';
    }
  }

  const acceptPendingMediaFile = useCallback(
    (file: File) => {
      const validationError = validateChatMediaFile(file);
      if (validationError) {
        setError(validationError);
        clearPendingFile();
        return;
      }
      setError(null);
      setPendingFile(file);
      window.requestAnimationFrame(() => {
        composerTextareaRef.current?.focus();
      });
    },
    [],
  );

  function handleFileSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    acceptPendingMediaFile(file);
  }

  async function handleCreateRoom(event: FormEvent) {
    event.preventDefault();
    const name = newRoomName.trim();
    if (!name) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const {data} = await chatApi.createRoom(name);
      const room = data.data;
      if (room) {
        setRooms(prev => [room, ...prev]);
        selectRoom(room.id);
        setNewRoomName('');
      }
    } catch {
      setError('Could not create room');
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteRoom(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room?.owner) {
      return;
    }
    if (
      !(await confirm({
        title: 'Delete room',
        message: `Delete "${room.name}"? This removes the room for all members.`,
        confirmLabel: 'Delete room',
        danger: true,
      }))
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    const wasSelected = selectedRoomId === roomId;
    setRooms(prev => prev.filter(entry => entry.id !== roomId));
    if (wasSelected) {
      setSelectedRoomId(null);
      setApiMessages([]);
      if (workspace.isMobile) {
        roomsDrawerDismissedRef.current = false;
        workspace.openRoomsDrawer();
      }
    }
    markChatRoomRemoved(roomId);
    try {
      await chatApi.deleteRoom(roomId);
    } catch (err) {
      unmarkChatRoomRemoved(roomId);
      await loadRooms();
      const apiMessage =
        axios.isAxiosError(err) &&
        typeof err.response?.data === 'object' &&
        err.response.data !== null &&
        'message' in err.response.data &&
        typeof (err.response.data as {message?: string}).message === 'string'
          ? (err.response.data as {message: string}).message
          : null;
      setError(apiMessage ?? 'Could not delete room');
    } finally {
      setBusy(false);
    }
  }

  async function handleClearChat(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room) {
      return;
    }
    if (
      !(await confirm({
        title: 'Clear chat',
        message: `Clear messages in "${room.name}" on this device only? Other members will still see the chat.`,
        confirmLabel: 'Clear chat',
        danger: true,
      }))
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    messagesLoadGenRef.current += 1;
    try {
      await chatApi.clearChat(roomId);
      clearPersonalChatForRoom(roomId);
      setApiMessages(prev => prev.filter(message => message.roomId !== roomId));
      setMessagesLoadedAt(prev => ({...prev, [roomId]: Date.now()}));
      if (selectedRoomId === roomId) {
        await loadMessages(roomId);
      }
      setNotice('Chat cleared on this device.');
    } catch {
      setError('Could not clear chat');
      if (selectedRoomId === roomId) {
        void loadMessages(roomId);
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleClearHistory(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room?.owner) {
      return;
    }
    if (
      !(await confirm({
        title: 'Clear history for everyone',
        message: `Remove all messages in "${room.name}" for every member? The room stays — only the owner can do this.`,
        confirmLabel: 'Clear history',
        danger: true,
      }))
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    messagesLoadGenRef.current += 1;
    try {
      await chatApi.clearHistory(roomId);
      clearLiveChatMessagesForRoom(roomId);
      setApiMessages(prev => prev.filter(message => message.roomId !== roomId));
      setMessagesLoadedAt(prev => ({...prev, [roomId]: Date.now()}));
      if (selectedRoomId === roomId) {
        await loadMessages(roomId);
      }
      setNotice('Chat history cleared.');
    } catch (err) {
      const apiMessage =
        axios.isAxiosError(err) &&
        err.response?.data &&
        typeof err.response.data === 'object' &&
        err.response.data !== null &&
        'message' in err.response.data &&
        typeof (err.response.data as {message?: string}).message === 'string'
          ? (err.response.data as {message: string}).message
          : null;
      setError(apiMessage ?? 'Could not clear chat history');
      if (selectedRoomId === roomId) {
        void loadMessages(roomId);
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleLeaveRoom(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room || room.owner) {
      return;
    }
    if (
      !(await confirm({
        title: 'Leave room',
        message: `Leave "${room.name}"? You can be invited again later.`,
        confirmLabel: 'Leave room',
        danger: true,
      }))
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    const wasSelected = selectedRoomId === roomId;
    setRooms(prev => prev.filter(entry => entry.id !== roomId));
    if (wasSelected) {
      setSelectedRoomId(null);
      setApiMessages([]);
      if (workspace.isMobile) {
        roomsDrawerDismissedRef.current = false;
        workspace.openRoomsDrawer();
      }
    }
    markChatRoomRemoved(roomId);
    try {
      await chatApi.leaveRoom(roomId);
    } catch {
      unmarkChatRoomRemoved(roomId);
      await loadRooms();
      setError('Could not leave room');
    } finally {
      setBusy(false);
    }
  }

  async function handleInvite(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId) {
      return;
    }
    const identifier = inviteIdentifier.trim();
    if (!identifier) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await chatApi.invite(selectedRoomId, identifier);
      setInviteIdentifier('');
      setShowInvite(false);
    } catch {
      setError('Could not send invite — check handle or email');
    } finally {
      setBusy(false);
    }
  }

  async function respondInvite(inviteId: string, accept: boolean) {
    setBusy(true);
    setError(null);
    try {
      const {data} = await chatApi.respondInvite(inviteId, accept);
      setInvites(prev => prev.filter(invite => invite.id !== inviteId));
      if (accept && data.data?.roomId) {
        selectRoom(data.data.roomId);
        void loadRooms();
      }
    } catch {
      setError('Could not respond to invite');
    } finally {
      setBusy(false);
    }
  }

  function catchUpRead() {
    if (!selectedRoomId) {
      return;
    }
    readMarkEpochRef.current += 1;
    suppressMarkReadRef.current = false;
    setUnreadDivider(null);
    const lastIncoming = [...visibleMessages].reverse().find(message => !message.ownMessage);
    if (!lastIncoming) {
      setReadCursor(null);
      return;
    }
    lastReadMarkedRef.current = lastIncoming.id;
    setReadCursor(lastIncoming.id);
    setAtBottom(true);
    void chatApi
      .markRead(selectedRoomId, lastIncoming.id)
      .then(() => {
        useRealtimeStore.getState().markRoomIncomingRead(selectedRoomId);
        setApiMessages(prev =>
          prev.map(message =>
            message.roomId === selectedRoomId && !message.ownMessage
              ? {...message, unread: false}
              : message,
          ),
        );
        setRooms(prev =>
          prev.map(room =>
            room.id === selectedRoomId
              ? {...room, lastReadMessageId: lastIncoming.id, unreadCount: 0}
              : room,
          ),
        );
      })
      .catch(() => undefined);
  }
  catchUpReadRef.current = catchUpRead;

  async function handleSendMessage(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId) {
      return;
    }
    const body = draft.trim();
    if (!body && !pendingFile) {
      return;
    }
    if (pendingFile && pendingFile.size === 0) {
      setError('Selected file is empty — pick the image again');
      return;
    }

    setBusy(true);
    setError(null);
    const replyId = replyingTo?.id;
    const fileToSend = pendingFile;
    // Clear composer immediately so focus stays usable for the next message.
    setDraft('');
    setReplyingTo(null);
    if (fileToSend) {
      clearPendingFile();
    }
    try {
      if (fileToSend) {
        const caption = body ? encodeChatMessagePlaintext(body) : undefined;
        if (caption && caption.length > 2000) {
          setError('Caption is too long after encoding — use fewer words');
          setDraft(body);
          return;
        }
        const {data} = await chatApi.sendMediaMessage(
          selectedRoomId,
          fileToSend,
          caption,
          replyId,
        );
        if (data.data) {
          setApiMessages(prev =>
            prev.some(message => message.id === data.data!.id) ? prev : [...prev, data.data!],
          );
        }
      } else {
        const obfuscatedBody = encodeChatMessagePlaintext(body);
        if (obfuscatedBody.length > 2000) {
          setError('Message is too long after encoding — use fewer words');
          setDraft(body);
          return;
        }
        const {data} = await chatApi.sendMessage(selectedRoomId, obfuscatedBody, replyId);
        if (data.data) {
          setApiMessages(prev =>
            prev.some(message => message.id === data.data!.id) ? prev : [...prev, data.data!],
          );
        }
      }
      // Sending advances the read cursor (WhatsApp) — atBottom effect will markRead.
      suppressMarkReadRef.current = false;
      setUnreadDivider(null);
      lastReadMarkedRef.current = null;
      setAtBottom(true);
    } catch {
      setError(fileToSend ? 'Could not send media' : 'Could not send message');
      setDraft(body);
      if (fileToSend) {
        setPendingFile(fileToSend);
      }
    } finally {
      setBusy(false);
      window.requestAnimationFrame(() => {
        composerTextareaRef.current?.focus();
      });
    }
  }

  function canDeleteMessage(_message: ChatMessageItem): boolean {
    return Boolean(selectedRoom);
  }

  function canDeleteForEveryone(message: ChatMessageItem): boolean {
    return Boolean(message.ownMessage);
  }

  function canEditMessage(message: ChatMessageItem): boolean {
    return Boolean(message.ownMessage);
  }

  function startEditMessage(message: ChatMessageItem) {
    setEditingMessageId(message.id);
    setEditDraft(message.body ?? '');
    setReplyingTo(null);
    setError(null);
    setDraft('');
  }

  function cancelEditMessage() {
    setEditingMessageId(null);
    setEditDraft('');
  }

  async function handleSaveEdit(message?: ChatMessageItem) {
    const target = message ?? editingMessage;
    if (!selectedRoomId || !target || !canEditMessage(target)) {
      return;
    }
    const trimmed = editDraft.trim();
    const isText = !target.messageType || target.messageType === 'TEXT';
    if (isText && !trimmed) {
      setError('Message cannot be empty');
      return;
    }
    if (trimmed === target.body) {
      cancelEditMessage();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const {data} = await chatApi.editMessage(selectedRoomId, target.id, trimmed);
      const updated = data.data;
      if (updated) {
        patchMessage(target.id, updated);
        useRealtimeStore.getState().upsertLiveChatMessage(updated);
      }
      cancelEditMessage();
    } catch {
      setError('Could not edit message');
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteMessage(message: ChatMessageItem, scope: 'FOR_ME' | 'FOR_EVERYONE') {
    if (!selectedRoomId || !canDeleteMessage(message)) {
      return;
    }
    if (scope === 'FOR_EVERYONE' && !canDeleteForEveryone(message)) {
      return;
    }
    const confirmed = await confirm({
      title: scope === 'FOR_EVERYONE' ? 'Delete for everyone' : 'Delete for me',
      message:
        scope === 'FOR_EVERYONE'
          ? 'Delete this message for all members?'
          : 'Delete this message on your device only? Other members will still see it.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    setBusy(true);
    setError(null);
    setApiMessages(prev => prev.filter(entry => entry.id !== message.id));
    removeLiveChatMessage(message.id);
    try {
      await chatApi.deleteMessage(selectedRoomId, message.id, scope);
    } catch {
      setError('Could not delete message');
      void loadMessages(selectedRoomId);
    } finally {
      setBusy(false);
    }
  }

  function startReplyMessage(message: ChatMessageItem) {
    setReplyingTo(message);
    setEditingMessageId(null);
    setEditDraft('');
    setError(null);
    window.requestAnimationFrame(() => {
      composerTextareaRef.current?.focus();
    });
  }

  function cancelReplyMessage() {
    setReplyingTo(null);
  }

  const mergeRoomMessages = useCallback((roomId: string, batch: ChatMessageItem[]) => {
    setApiMessages(prev => {
      const otherRooms = prev.filter(message => message.roomId !== roomId);
      const merged = new Map<string, ChatMessageItem>();
      for (const message of prev) {
        if (message.roomId === roomId) {
          merged.set(message.id, message);
        }
      }
      for (const message of batch) {
        merged.set(message.id, message);
      }
      return [...otherRooms, ...Array.from(merged.values())];
    });
  }, []);

  const loadOlderMessagesUntil = useCallback(
    async (roomId: string, messageId: string) => {
      const pageSize = 50;
      for (let page = 1; page < 20; page += 1) {
        const {data} = await chatApi.listMessages(roomId, page, pageSize);
        const batch = data.data ?? [];
        if (batch.length === 0) {
          break;
        }
        mergeRoomMessages(roomId, batch);
        if (batch.some(message => message.id === messageId)) {
          return true;
        }
        if (batch.length < pageSize) {
          break;
        }
      }
      return false;
    },
    [mergeRoomMessages],
  );

  const revealQuotedMessage = useCallback((messageId: string) => {
    highlightClearRef.current?.();
    highlightClearRef.current = null;

    const scrolled = scrollChatMessageIntoView(messagesScrollRef.current, messageId);
    if (scrolled) {
      highlightClearRef.current = flashChatMessageHighlight(messageId);
    }
  }, []);

  const scrollToMessage = useCallback(
    (messageId: string) => {
      if (!selectedRoomId) {
        return;
      }

      const messageLoaded = messages.some(message => message.id === messageId);
      const messageVisible = visibleMessages.some(message => message.id === messageId);

      if (messageLoaded && !messageVisible && searchQuery.trim()) {
        pendingScrollMessageIdRef.current = messageId;
        setSearchQuery('');
        return;
      }

      if (messageLoaded) {
        window.requestAnimationFrame(() => {
          revealQuotedMessage(messageId);
        });
        return;
      }

      pendingScrollMessageIdRef.current = messageId;
      void loadOlderMessagesUntil(selectedRoomId, messageId)
        .then(found => {
          if (!found) {
            pendingScrollMessageIdRef.current = null;
            setNotice('Original message is no longer available in this chat.');
            return;
          }
          if (pendingScrollMessageIdRef.current !== messageId) {
            return;
          }
        })
        .catch(() => {
          pendingScrollMessageIdRef.current = null;
          setError('Could not jump to the quoted message');
        });
    },
    [loadOlderMessagesUntil, messages, revealQuotedMessage, searchQuery, selectedRoomId, visibleMessages],
  );

  const scrollToFirstUnread = useCallback(() => {
    if (firstUnreadId) {
      scrollToMessage(firstUnreadId);
    }
  }, [firstUnreadId, scrollToMessage]);

  useEffect(() => {
    const messageId = pendingScrollMessageIdRef.current;
    if (!messageId || !visibleMessages.some(message => message.id === messageId)) {
      return;
    }
    pendingScrollMessageIdRef.current = null;
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        revealQuotedMessage(messageId);
      });
    });
  }, [visibleMessages, revealQuotedMessage]);

  useEffect(
    () => () => {
      highlightClearRef.current?.();
    },
    [],
  );

  const patchMessageReactions = useCallback((messageId: string, reactions: ChatMessageItem['reactions']) => {
    setApiMessages(prev =>
      prev.map(message => (message.id === messageId ? {...message, reactions} : message)),
    );
    useRealtimeStore.getState().updateLiveChatMessageReactions(messageId, reactions);
  }, []);

  const handleToggleReaction = useCallback(
    (message: ChatMessageItem, emoji: string) => {
      if (!selectedRoomId || busy) {
        return;
      }
      void chatApi
        .setMessageReaction(selectedRoomId, message.id, emoji)
        .then(({data}) => {
          patchMessageReactions(message.id, data.data ?? []);
        })
        .catch(() => {
          setError('Could not update reaction');
        });
    },
    [busy, patchMessageReactions, selectedRoomId],
  );

  function handleCopyMessage(message: ChatMessageItem) {
    if (!message.body) {
      return;
    }
    void navigator.clipboard.writeText(message.body).catch(() => {
      setError('Could not copy message');
    });
  }

  async function handleMarkUnread(message: ChatMessageItem) {
    if (!selectedRoomId || busy) {
      return;
    }
    lastReadMarkedRef.current = null;
    readMarkEpochRef.current += 1;
    const previousId = previousMessageId(visibleMessages, message.id);
    setReadCursor(previousId);
    setAtBottom(false);
    suppressMarkReadRef.current = true;
    const held = unreadAfterCursor(visibleMessages, previousId);
    setUnreadDivider({
      firstId: message.id,
      count: Math.max(1, held.length),
    });
    setRooms(prev =>
      prev.map(room =>
        room.id === selectedRoomId
          ? {
              ...room,
              lastReadMessageId: previousId,
              unreadCount: Math.max(1, held.length),
            }
          : room,
      ),
    );
    try {
      await chatApi.markUnread(selectedRoomId, message.id);
    } catch {
      setError('Could not mark as unread');
      void loadMessages(selectedRoomId);
      void loadRooms();
    }
  }

  function renderMessageBody(message: ChatMessageItem) {
    const isMedia = message.messageType === 'IMAGE' || message.messageType === 'VIDEO';
    const showMedia =
      isMedia &&
      (message.attachmentUrl ||
        message.attachmentViewOnce ||
        message.canViewAttachment ||
        message.attachmentConsumed);

    return (
      <>
        {showMedia ? <ChatMessageMedia message={message} onConsumed={patchMessage} /> : null}
        {message.body ? (
          <ChatMessageText
            text={message.body}
            mentionTokens={mentionTokens}
            edited={Boolean(message.editedAt)}
          />
        ) : null}
        {!isMedia && !message.body ? <p className="chat-message-body muted">—</p> : null}
      </>
    );
  }

  function renderMessage(message: ChatMessageItem) {
    const displayName = resolveChatDisplayNickname(message.senderNickname, message.senderLabel);
    return (
      <MessageBubble
        key={message.id}
        message={message}
        displayName={displayName}
        senderSeed={message.senderUserId}
        ownDisplayName={ownDisplayName}
        ownSeed={ownSeed}
        busy={busy}
        isEditing={editingMessageId === message.id}
        canEdit={canEditMessage(message)}
        canDelete={canDeleteMessage(message)}
        canDeleteForEveryone={canDeleteForEveryone(message)}
        swipeToReplyEnabled={workspace.isMobile}
        onEdit={() => startEditMessage(message)}
        onDeleteForMe={() => void handleDeleteMessage(message, 'FOR_ME')}
        onDeleteForEveryone={() => void handleDeleteMessage(message, 'FOR_EVERYONE')}
        onCopy={() => handleCopyMessage(message)}
        onReply={() => startReplyMessage(message)}
        onQuoteClick={scrollToMessage}
        onToggleReaction={emoji => handleToggleReaction(message, emoji)}
        onMarkUnread={() => void handleMarkUnread(message)}>
        {renderMessageBody(message)}
      </MessageBubble>
    );
  }

  const headerMenuActions = selectedRoom
    ? [
        ...(unreadCount > 0
          ? [
              {
                id: 'mark-read',
                label: 'Mark as read',
                disabled: busy,
                onSelect: () => catchUpRead(),
              },
            ]
          : []),
        {
          id: 'clear-chat',
          label: 'Clear chat',
          disabled: busy,
          onSelect: () => void handleClearChat(selectedRoom.id),
        },
        ...(selectedRoom.owner
          ? [
              {
                id: 'clear-history',
                label: 'Clear history',
                disabled: busy,
                onSelect: () => void handleClearHistory(selectedRoom.id),
              },
              {
                id: 'delete',
                label: 'Delete room',
                danger: true,
                disabled: busy,
                onSelect: () => void handleDeleteRoom(selectedRoom.id),
              },
            ]
          : [
              {
                id: 'leave',
                label: 'Leave room',
                disabled: busy,
                onSelect: () => void handleLeaveRoom(selectedRoom.id),
              },
            ]),
      ]
    : [];

  const renderRoomsPanel = () => (
    <ChatRoomsPanel
      rooms={rooms}
      selectedRoomId={selectedRoomId}
      newRoomName={newRoomName}
      busy={busy}
      swipeEnabled={workspace.isMobile}
      showClose={!workspace.isMobile}
      onSelectRoom={selectRoom}
      onRemoveRoom={roomId => void handleRemoveRoom(roomId)}
      onClose={workspace.closeRoomsPanel}
      onNewRoomNameChange={setNewRoomName}
      onCreateRoom={handleCreateRoom}
    />
  );

  return (
    <div className="chat-page">
      {confirmDialog}
      {invites.length > 0 ? (
        <div className="chat-invites-banner">
          <div className="chat-invites-banner-head">
            <strong>
              {invites.length} pending invite{invites.length === 1 ? '' : 's'}
            </strong>
          </div>
          <ul className="chat-invites-list">
            {invites.map(invite => (
              <li key={invite.id} className="chat-invite-row-item">
                <div>
                  <span className="chat-invite-room">{invite.roomName}</span>
                  <span className="muted tiny">
                    {' '}
                    from{' '}
                    {resolveChatDisplayNickname(invite.inviterNickname, invite.inviterLabel)}
                  </span>
                </div>
                <div className="chat-invite-actions">
                  <button
                    type="button"
                    className="btn primary sm"
                    disabled={busy}
                    onClick={() => void respondInvite(invite.id, true)}>
                    Accept
                  </button>
                  <button
                    type="button"
                    className="btn ghost sm"
                    disabled={busy}
                    onClick={() => void respondInvite(invite.id, false)}>
                    Decline
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {error ? <div className="chat-error">{error}</div> : null}
      {notice ? <div className="chat-notice">{notice}</div> : null}

      <ChatWorkspaceLayout
        layout={workspace}
        mobileThreadVisible
        onMobileBackToApp={() => navigate('/devices', {replace: true})}
        onCloseRoomsDrawer={handleCloseRoomsDrawer}
        renderRooms={renderRoomsPanel}
        thread={
          <section className="chat-thread-panel">
            {!selectedRoom ? (
              <div className="chat-empty">
                {!workspace.roomsOpen && !workspace.isMobile ? (
                  <button
                    type="button"
                    className="chat-info-reopen-btn chat-rooms-reopen-btn"
                    aria-label="Show rooms panel"
                    title="Show rooms"
                    onClick={workspace.openRoomsPanel}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                      <path d="M4 6h16M4 12h16M4 18h10" />
                    </svg>
                    <span>Rooms</span>
                  </button>
                ) : null}
                {workspace.isMobile && !workspace.roomsDrawerOpen ? (
                  <button
                    type="button"
                    className="chat-info-reopen-btn chat-rooms-reopen-btn"
                    aria-label="Open rooms list"
                    title="Open rooms"
                    onClick={workspace.openRoomsDrawer}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                      <path d="M4 6h16M4 12h16M4 18h10" />
                    </svg>
                    <span>Rooms</span>
                  </button>
                ) : null}
                {!workspace.infoOpen ? (
                  <button
                    type="button"
                    className="chat-info-reopen-btn"
                    aria-label="Show parent chat help"
                    title="Parent chat help"
                    onClick={workspace.openInfoPanel}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                      <circle cx="12" cy="12" r="9" />
                      <path d="M12 10v6M12 7h.01" />
                    </svg>
                    <span>Help</span>
                  </button>
                ) : null}
                <Avatar name="Chat" seed="chat-empty" size="lg" />
                <h2>Select a room</h2>
                <p className="muted">
                  Pick a room from the list, or create one with the + button.
                </p>
              </div>
            ) : (
              <ChatFileDropZone
                enabled={!busy && !editingMessageId}
                onFile={acceptPendingMediaFile}>
                <ChatInviteBar
                  visible={showInvite}
                  identifier={inviteIdentifier}
                  busy={busy}
                  onIdentifierChange={setInviteIdentifier}
                  onSubmit={handleInvite}
                />

                <ChatHeader
                  room={selectedRoom}
                  showInvite={showInvite}
                  busy={busy}
                  searchOpen={searchOpen}
                  menuActions={headerMenuActions}
                  infoPanelOpen={workspace.infoOpen}
                  showInfoPanelToggle={!workspace.infoOpen}
                  showRoomsPanelToggle={!workspace.isMobile && !workspace.roomsOpen}
                  onOpenRoomsPanel={workspace.openRoomsPanel}
                  showBack={workspace.isMobile}
                  onBack={handleMobileBackFromChat}
                  showRoomsMenu={workspace.isMobile}
                  onOpenRooms={workspace.openRoomsDrawer}
                  compact={workspace.isMobile}
                  onOpenInfoPanel={workspace.openInfoPanel}
                  onToggleInvite={() => setShowInvite(prev => !prev)}
                  onToggleSearch={() => setSearchOpen(prev => !prev)}
                />

                {searchOpen ? (
                  <div className="chat-search-bar">
                    <input
                      type="search"
                      placeholder="Search messages…"
                      value={searchQuery}
                      aria-label="Search messages"
                      onChange={event => setSearchQuery(event.target.value)}
                    />
                    {searchQuery ? (
                      <button
                        type="button"
                        className="btn ghost sm"
                        onClick={() => setSearchQuery('')}>
                        Clear
                      </button>
                    ) : null}
                  </div>
                ) : null}

                <div
                  className="chat-messages"
                  ref={messagesScrollRef}
                  onWheel={() => {
                    if (suppressMarkReadRef.current) {
                      suppressMarkReadRef.current = false;
                    }
                  }}
                  onTouchMove={() => {
                    if (suppressMarkReadRef.current) {
                      suppressMarkReadRef.current = false;
                    }
                  }}>
                  {unreadCount > 0 && !searchQuery.trim() ? (
                    <button
                      type="button"
                      className="chat-unread-banner"
                      onClick={scrollToFirstUnread}>
                      {unreadCount === 1 ? '1 unread message' : `${unreadCount} unread messages`}
                    </button>
                  ) : null}
                  {messages.length === 0 ? (
                    <EmptyChatState
                      title={`Start a conversation with ${selectedRoom.name}`}
                      subtitle="Send a message to get started, or invite another parent to join."
                      avatarName={selectedRoom.name}
                      avatarSeed={selectedRoom.id}
                      showInvite={selectedRoom.owner}
                      inviteIdentifier={inviteIdentifier}
                      busy={busy}
                      onInviteIdentifierChange={setInviteIdentifier}
                      onInvite={handleInvite}
                    />
                  ) : visibleMessages.length === 0 ? (
                    <p className="chat-messages-empty muted">No messages match your search.</p>
                  ) : (
                    messageGroups.map(group => (
                      <Fragment key={group.dateKey}>
                        <DateSeparator label={group.label} />
                        {group.messages.map(message => (
                          <Fragment key={message.id}>
                            {message.id === firstUnreadId ? (
                              <UnreadSeparator
                                count={unreadCount}
                                onClick={scrollToFirstUnread}
                              />
                            ) : null}
                            {renderMessage(message)}
                          </Fragment>
                        ))}
                      </Fragment>
                    ))
                  )}
                  <div ref={messagesEndRef} />
                </div>

                <div className="chat-compose-footer">
                {pendingFile ? (
                  <div className="chat-compose-preview">
                    {pendingPreviewUrl && pendingFile.type.startsWith('video/') ? (
                      <video
                        className="chat-compose-preview-media"
                        src={pendingPreviewUrl}
                        muted
                        playsInline
                        preload="metadata"
                        aria-label={pendingFile.name}
                      />
                    ) : pendingPreviewUrl ? (
                      <img
                        className="chat-compose-preview-media"
                        src={pendingPreviewUrl}
                        alt={pendingFile.name}
                      />
                    ) : (
                      <div className="chat-compose-preview-media chat-compose-preview-media--placeholder" />
                    )}
                    <div className="chat-compose-preview-meta">
                      <span className="chat-compose-preview-name" title={pendingFile.name}>
                        {pendingFile.name}
                      </span>
                      <button
                        type="button"
                        className="chat-compose-preview-remove"
                        disabled={busy}
                        aria-label="Remove attachment"
                        onClick={clearPendingFile}>
                        ×
                      </button>
                    </div>
                  </div>
                ) : null}

                {replyingTo && !editingMessage ? (
                  <ComposerReplyBar
                    preview={replyPreviewFromMessage(replyingTo)}
                    onDismiss={cancelReplyMessage}
                  />
                ) : null}

                {editingMessage ? (
                  <EditMessageComposer
                    draft={editDraft}
                    busy={busy}
                    isText={!editingMessage.messageType || editingMessage.messageType === 'TEXT'}
                    onDraftChange={setEditDraft}
                    onSave={() => void handleSaveEdit(editingMessage)}
                    onCancel={cancelEditMessage}
                  />
                ) : (
                  <MessageComposer
                    draft={draft}
                    placeholder={
                      pendingFile
                        ? `Caption for ${pendingFile.name} (optional)`
                        : 'Write a message… (@ to mention)'
                    }
                    busy={busy}
                    canSend={Boolean(draft.trim() || pendingFile)}
                    emphasizeSend={Boolean(pendingFile)}
                    members={mentionableMembers}
                    fileInputRef={composerFileInputRef}
                    textareaRef={composerTextareaRef}
                    onDraftChange={setDraft}
                    onSubmit={handleSendMessage}
                    onFileSelected={handleFileSelected}
                  />
                )}
                </div>
              </ChatFileDropZone>
            )}
          </section>
        }
      />
    </div>
  );
}
