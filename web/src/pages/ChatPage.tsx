import {Fragment, type FormEvent, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import axios from 'axios';
import {chatApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {encodeChatMessagePlaintext} from '../lib/chatMessageObfuscation';
import {groupMessagesByDate} from '../lib/chatMessageGrouping';
import {resolveChatDisplayNickname} from '../lib/chatDisplayNickname';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {Avatar} from '../components/Avatar';
import {ChatMessageMedia} from '../components/ChatMessageMedia';
import {ChatHeader} from '../components/chat/ChatHeader';
import {ChatInviteBar} from '../components/chat/ChatInviteBar';
import {DateSeparator} from '../components/chat/DateSeparator';
import {EmptyChatState} from '../components/chat/EmptyChatState';
import {MessageBubble} from '../components/chat/MessageBubble';
import {EditMessageComposer, MessageComposer} from '../components/chat/MessageComposer';
import type {ChatInviteItem, ChatMessageItem, ChatRoomSummary} from '../types/api';

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const MAX_VIDEO_BYTES = 50 * 1024 * 1024;
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);
const VIDEO_TYPES = new Set(['video/mp4', 'video/webm', 'video/quicktime']);

export function ChatPage() {
  const location = useLocation();
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
  const [showInvite, setShowInvite] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const composerFileInputRef = useRef<HTMLInputElement>(null);
  const initialRoomLoadRef = useRef(true);
  const messagesLoadGenRef = useRef(0);

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
  const syncLiveChatMessagesFromApi = useRealtimeStore(s => s.syncLiveChatMessagesFromApi);
  const chatInvitesEpoch = useRealtimeStore(s => s.chatInvitesEpoch);
  const liveChatMessages = useRealtimeStore(s => s.liveChatMessages);
  const deletedChatMessageIds = useRealtimeStore(s => s.deletedChatMessageIds);
  const chatClearedEpoch = useRealtimeStore(s => s.chatClearedEpoch);
  const chatClearedAt = useRealtimeStore(s => s.chatClearedAt);
  const removeLiveChatMessage = useRealtimeStore(s => s.removeLiveChatMessage);
  const receiptPatches = useRealtimeStore(s => s.receiptPatches);
  const deliveredMarkedRef = useRef<Set<string>>(new Set());
  const lastReadMarkedRef = useRef<string | null>(null);

  const selectedRoom = useMemo(
    () => rooms.find(room => room.id === selectedRoomId) ?? null,
    [rooms, selectedRoomId],
  );

  const messages = useMemo(() => {
    if (!selectedRoomId) {
      return [];
    }
    const loadedAt = messagesLoadedAt[selectedRoomId] ?? 0;
    const clearedAt = chatClearedAt[selectedRoomId];
    const byId = new Map<string, ChatMessageItem>();

    for (const message of apiMessages) {
      if (message.roomId !== selectedRoomId) {
        continue;
      }
      const createdMs = new Date(message.createdAt).getTime();
      if (clearedAt && createdMs <= clearedAt) {
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
        if (clearedAt && createdMs <= clearedAt) {
          continue;
        }
        if (createdMs <= loadedAt && !byId.has(message.id)) {
          continue;
        }
        const existing = byId.get(message.id);
        byId.set(message.id, existing ? {...existing, ...message} : message);
      }
    }

    return Array.from(byId.values())
      .map(message => {
        const patch = receiptPatches[message.id];
        return patch ? {...message, receiptStatus: patch} : message;
      })
      .sort(
        (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
      );
  }, [
    apiMessages,
    liveChatMessages,
    selectedRoomId,
    deletedChatMessageIds,
    chatClearedAt,
    messagesLoadedAt,
    receiptPatches,
  ]);

  const visibleMessages = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      return messages;
    }
    return messages.filter(message => message.body?.toLowerCase().includes(query));
  }, [messages, searchQuery]);

  const messageGroups = useMemo(
    () => groupMessagesByDate(visibleMessages),
    [visibleMessages],
  );

  const editingMessage = useMemo(
    () => messages.find(message => message.id === editingMessageId) ?? null,
    [messages, editingMessageId],
  );

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
          return next[0]?.id ?? null;
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
  }, [pruneLiveChatMessages, unmarkChatRoomRemoved]);

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
  }, [selectedRoomId, loadMessages]);

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
    deliveredMarkedRef.current.clear();
    lastReadMarkedRef.current = null;
  }, [selectedRoomId]);

  useEffect(() => {
    if (!selectedRoomId || messages.length === 0) {
      return;
    }
    const pending = messages
      .filter(message => !message.ownMessage && !deliveredMarkedRef.current.has(message.id))
      .map(message => message.id);
    if (pending.length === 0) {
      return;
    }
    pending.forEach(id => deliveredMarkedRef.current.add(id));
    void chatApi.markDelivered(selectedRoomId, pending).catch(() => undefined);
  }, [selectedRoomId, messages]);

  useEffect(() => {
    if (!selectedRoomId || messages.length === 0) {
      return;
    }
    const lastIncoming = [...messages].reverse().find(message => !message.ownMessage);
    if (!lastIncoming || lastIncoming.id === lastReadMarkedRef.current) {
      return;
    }
    lastReadMarkedRef.current = lastIncoming.id;
    void chatApi.markRead(selectedRoomId, lastIncoming.id).catch(() => undefined);
  }, [selectedRoomId, messages]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
  }, [messages, searchQuery]);

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

  function validateMediaFile(file: File): string | null {
    const mime = file.type.toLowerCase();
    if (IMAGE_TYPES.has(mime)) {
      return file.size > MAX_IMAGE_BYTES ? 'Image must be 10 MB or smaller' : null;
    }
    if (VIDEO_TYPES.has(mime)) {
      return file.size > MAX_VIDEO_BYTES ? 'Video must be 50 MB or smaller' : null;
    }
    return 'Only JPEG, PNG, WebP, GIF images or MP4, WebM, MOV videos are allowed';
  }

  function handleFileSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const validationError = validateMediaFile(file);
    if (validationError) {
      setError(validationError);
      clearPendingFile();
      return;
    }
    setError(null);
    setPendingFile(file);
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
        setSelectedRoomId(room.id);
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
    if (!window.confirm(`Delete "${room.name}"? This removes the room for all members.`)) {
      return;
    }
    setBusy(true);
    setError(null);
    const wasSelected = selectedRoomId === roomId;
    setRooms(prev => prev.filter(entry => entry.id !== roomId));
    if (wasSelected) {
      setSelectedRoomId(null);
      setApiMessages([]);
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

  async function handleClearHistory(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room?.owner) {
      return;
    }
    if (
      !window.confirm(
        `Clear all messages in "${room.name}"? The room and members stay — only chat history is removed.`,
      )
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
    if (!window.confirm(`Leave "${room.name}"? You can be invited again later.`)) {
      return;
    }
    setBusy(true);
    setError(null);
    const wasSelected = selectedRoomId === roomId;
    setRooms(prev => prev.filter(entry => entry.id !== roomId));
    if (wasSelected) {
      setSelectedRoomId(null);
      setApiMessages([]);
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
        setSelectedRoomId(data.data.roomId);
        void loadRooms();
      }
    } catch {
      setError('Could not respond to invite');
    } finally {
      setBusy(false);
    }
  }

  async function handleSendMessage(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId) {
      return;
    }
    const body = draft.trim();
    if (!body && !pendingFile) {
      return;
    }

    setBusy(true);
    setError(null);
    try {
      if (pendingFile) {
        const caption = body ? encodeChatMessagePlaintext(body) : undefined;
        if (caption && caption.length > 2000) {
          setError('Caption is too long after encoding — use fewer words');
          return;
        }
        const {data} = await chatApi.sendMediaMessage(selectedRoomId, pendingFile, caption);
        if (data.data) {
          setApiMessages(prev =>
            prev.some(message => message.id === data.data!.id) ? prev : [...prev, data.data!],
          );
        }
        clearPendingFile();
      } else {
        const obfuscatedBody = encodeChatMessagePlaintext(body);
        if (obfuscatedBody.length > 2000) {
          setError('Message is too long after encoding — use fewer words');
          return;
        }
        const {data} = await chatApi.sendMessage(selectedRoomId, obfuscatedBody);
        if (data.data) {
          setApiMessages(prev =>
            prev.some(message => message.id === data.data!.id) ? prev : [...prev, data.data!],
          );
        }
      }
      setDraft('');
    } catch {
      setError(pendingFile ? 'Could not send media' : 'Could not send message');
    } finally {
      setBusy(false);
    }
  }

  function canDeleteMessage(message: ChatMessageItem): boolean {
    if (!selectedRoom) {
      return false;
    }
    return Boolean(message.ownMessage || selectedRoom.owner);
  }

  function canEditMessage(message: ChatMessageItem): boolean {
    return Boolean(message.ownMessage);
  }

  function startEditMessage(message: ChatMessageItem) {
    setEditingMessageId(message.id);
    setEditDraft(message.body ?? '');
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

  async function handleDeleteMessage(message: ChatMessageItem) {
    if (!selectedRoomId || !canDeleteMessage(message)) {
      return;
    }
    const confirmText = message.ownMessage
      ? 'Delete this message for everyone?'
      : 'Delete this message?';
    if (!window.confirm(confirmText)) {
      return;
    }
    setBusy(true);
    setError(null);
    setApiMessages(prev => prev.filter(entry => entry.id !== message.id));
    removeLiveChatMessage(message.id);
    try {
      await chatApi.deleteMessage(selectedRoomId, message.id);
    } catch {
      setError('Could not delete message');
      void loadMessages(selectedRoomId);
    } finally {
      setBusy(false);
    }
  }

  function handleCopyMessage(message: ChatMessageItem) {
    if (!message.body) {
      return;
    }
    void navigator.clipboard.writeText(message.body).catch(() => {
      setError('Could not copy message');
    });
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
          <p className="chat-message-body">
            {message.body}
            {message.editedAt ? <span className="chat-message-edited"> (edited)</span> : null}
          </p>
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
        onEdit={() => startEditMessage(message)}
        onDelete={() => void handleDeleteMessage(message)}
        onCopy={() => handleCopyMessage(message)}>
        {renderMessageBody(message)}
      </MessageBubble>
    );
  }

  const headerMenuActions = selectedRoom
    ? selectedRoom.owner
      ? [
          {
            id: 'clear',
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
        ]
    : [];

  return (
    <div className="chat-page">
      {rooms.length > 0 ? (
        <div className="chat-room-mobile-select">
          <label htmlFor="chat-room-mobile-select">Room</label>
          <select
            id="chat-room-mobile-select"
            value={selectedRoomId ?? ''}
            onChange={event => setSelectedRoomId(event.target.value || null)}>
            {rooms.map(room => (
              <option key={room.id} value={room.id}>
                {room.name}
              </option>
            ))}
          </select>
          <form className="chat-create-mobile" onSubmit={handleCreateRoom}>
            <input
              type="text"
              placeholder="New room"
              value={newRoomName}
              maxLength={64}
              onChange={event => setNewRoomName(event.target.value)}
            />
            <button type="submit" className="btn primary sm" disabled={busy || !newRoomName.trim()}>
              +
            </button>
          </form>
        </div>
      ) : null}

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

      <div className="chat-layout">
        <aside className="chat-rooms-panel">
          <div className="chat-panel-head">
            <h2>Rooms</h2>
            {rooms.length > 0 ? <span className="chat-panel-count">{rooms.length}</span> : null}
          </div>
          <div className="chat-room-list">
            {rooms.length === 0 ? (
              <p className="chat-room-empty">No rooms yet</p>
            ) : (
              rooms.map(room => (
                <div
                  key={room.id}
                  className={`chat-room-item${room.id === selectedRoomId ? ' active' : ''}`}>
                  <button
                    type="button"
                    className="chat-room-select"
                    onClick={() => setSelectedRoomId(room.id)}>
                    <Avatar name={room.name} seed={room.id} size="md" />
                    <span className="chat-room-meta">
                      <span className="chat-room-name">{room.name}</span>
                      <span className="chat-room-sub">
                        {room.memberCount} member{room.memberCount === 1 ? '' : 's'}
                        {room.owner ? ' · owner' : ''}
                      </span>
                    </span>
                  </button>
                  {room.owner ? (
                    <button
                      type="button"
                      className="chat-room-delete"
                      title={`Delete ${room.name}`}
                      aria-label={`Delete ${room.name}`}
                      disabled={busy}
                      onClick={() => void handleDeleteRoom(room.id)}>
                      ×
                    </button>
                  ) : null}
                </div>
              ))
            )}
          </div>
          <form className="chat-create" onSubmit={handleCreateRoom}>
            <input
              type="text"
              placeholder="New room name"
              value={newRoomName}
              maxLength={64}
              onChange={event => setNewRoomName(event.target.value)}
            />
            <button type="submit" className="btn primary" disabled={busy || !newRoomName.trim()}>
              +
            </button>
          </form>
        </aside>

        <section className="chat-thread-panel">
          {!selectedRoom ? (
            <div className="chat-empty">
              <Avatar name="Chat" seed="chat-empty" size="lg" />
              <h2>Select a room</h2>
              <p className="muted">
                Pick a room from the list, or create one with the + button.
              </p>
            </div>
          ) : (
            <>
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

              <div className="chat-messages">
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
                      {group.messages.map(message => renderMessage(message))}
                    </Fragment>
                  ))
                )}
                <div ref={messagesEndRef} />
              </div>

              {pendingFile ? (
                <div className="chat-compose-preview">
                  {pendingPreviewUrl && pendingFile.type.startsWith('video/') ? (
                    <video className="chat-compose-preview-media" src={pendingPreviewUrl} controls />
                  ) : pendingPreviewUrl ? (
                    <img
                      className="chat-compose-preview-media"
                      src={pendingPreviewUrl}
                      alt={pendingFile.name}
                    />
                  ) : null}
                  <div className="chat-compose-preview-meta">
                    <span className="tiny">{pendingFile.name}</span>
                    <button
                      type="button"
                      className="btn ghost sm"
                      disabled={busy}
                      onClick={clearPendingFile}>
                      Remove
                    </button>
                  </div>
                </div>
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
                      : 'Write a message…'
                  }
                  busy={busy}
                  canSend={Boolean(draft.trim() || pendingFile)}
                  fileInputRef={composerFileInputRef}
                  onDraftChange={setDraft}
                  onSubmit={handleSendMessage}
                  onFileSelected={handleFileSelected}
                />
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
